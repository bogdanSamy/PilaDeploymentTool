#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

STATUS_FILE="$SCRIPT_DIR/restart_status.json"
LOCK_FILE="$SCRIPT_DIR/restart_status.lock"
ACTIVE_RESTART_FILE="$SCRIPT_DIR/active_restart.dat"

REAL_RESTART_SCRIPT="/beaadm/domain/h2o/12d/system/dINdomain/restartINServer.sh"
REAL_RESTART_ARG="serverdIN1"

APP_USERNAME="$1"
ACTION="$2"

APPROVAL_TIMEOUT=30

# ============================================================================
# ATOMIC LOCK
# ============================================================================

acquire_lock() {
    local max_attempts=50
    local attempt=0

    set -o noclobber

    while ! echo "$$" > "$LOCK_FILE" 2>/dev/null; do
        if [ $attempt -ge $max_attempts ]; then
            set +o noclobber
            return 1
        fi

        if [ -f "$LOCK_FILE" ]; then
            local old_pid=$(cat "$LOCK_FILE" 2>/dev/null)
            if ! kill -0 "$old_pid" 2>/dev/null; then
                rm -f "$LOCK_FILE"
            fi
        fi

        sleep 0.1
        ((attempt++))
    done

    set +o noclobber
    trap "rm -f $LOCK_FILE" EXIT INT TERM
    return 0
}

release_lock() {
    rm -f "$LOCK_FILE"
    trap - EXIT INT TERM
}

# ============================================================================
# JSON helpers — PENTRU STATUS_FILE (top-level fields only)
# ============================================================================

get_json_field() {
    local json="$1"
    local field="$2"
    local result=$(echo "$json" | grep -o "\"$field\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
    if [ -z "$result" ]; then
        result=$(echo "$json" | grep -o "\"$field\"[[:space:]]*:[[:space:]]*[0-9]*" | head -1 | grep -o '[0-9]*$')
    fi
    if [ -z "$result" ]; then
        result=$(echo "$json" | grep -o "\"$field\"[[:space:]]*:[[:space:]]*null" | head -1 | awk '{print "null"}')
    fi
    echo "$result"
}

# ============================================================================
# ACTIVE RESTART — fisier .dat cu campuri pe linii separate (NU JSON!)
#
# Format simplu, o linie per camp:
#   Linia 1: requester
#   Linia 2: project
#   Linia 3: started_at
#   Linia 4: requested_at
# ============================================================================

write_active_restart() {
    local requester="$1"
    local project="$2"
    local started_at="$3"
    local requested_at="$4"

    printf '%s\n%s\n%s\n%s\n' "$requester" "$project" "$started_at" "$requested_at" > "$ACTIVE_RESTART_FILE"
    chmod 666 "$ACTIVE_RESTART_FILE"
}

# Citeste un camp specific din .dat (1=requester, 2=project, 3=started_at, 4=requested_at)
read_active_field() {
    local line_num="$1"
    if [ -f "$ACTIVE_RESTART_FILE" ]; then
        sed -n "${line_num}p" "$ACTIVE_RESTART_FILE"
    else
        echo ""
    fi
}

ar_requester()    { read_active_field 1; }
ar_project()      { read_active_field 2; }
ar_started_at()   { read_active_field 3; }
ar_requested_at() { read_active_field 4; }

clear_active_restart() {
    rm -f "$ACTIVE_RESTART_FILE"
}

has_active_restart() {
    if [ -f "$ACTIVE_RESTART_FILE" ]; then
        local started=$(ar_started_at)
        if [ -n "$started" ] && [ "$started" != "0" ] && [ "$started" != "null" ]; then
            return 0
        fi
    fi
    return 1
}

# Genereaza blocul JSON pentru active_restart (apelat doar la scriere status)
active_restart_json_block() {
    if has_active_restart; then
        local req=$(ar_requester)
        local proj=$(ar_project)
        local started=$(ar_started_at)
        local req_at=$(ar_requested_at)

        local proj_val="null"
        if [ -n "$proj" ] && [ "$proj" != "null" ]; then
            proj_val="\"$proj\""
        fi

        echo "{\"requester\": \"$req\", \"project\": $proj_val, \"started_at\": $started, \"requested_at\": $req_at}"
    else
        echo "null"
    fi
}

# ============================================================================
# Status management
# ============================================================================

write_status() {
    local status="$1"
    local requester="$2"
    local project="$3"
    local requested_at="$4"
    local wait_until="$5"
    local in_progress="$6"
    local rejections="$7"

    local project_val="null"
    if [ -n "$project" ] && [ "$project" != "null" ]; then
        project_val="\"$project\""
    fi

    local req_at_val="null"
    if [ -n "$requested_at" ] && [ "$requested_at" != "null" ] && [ "$requested_at" != "0" ]; then
        req_at_val="$requested_at"
    fi

    local wait_val="null"
    if [ -n "$wait_until" ] && [ "$wait_until" != "null" ] && [ "$wait_until" != "0" ]; then
        wait_val="$wait_until"
    fi

    if [ -z "$rejections" ]; then
        rejections="[]"
    fi

    # Construieste blocul active_restart INAINTE de heredoc
    local ar_block=$(active_restart_json_block)

    cat > "$STATUS_FILE" <<EOF
{
    "version": $(date +%s),
    "in_progress": $in_progress,
    "requester": "$requester",
    "project": $project_val,
    "requested_at": $req_at_val,
    "wait_until": $wait_val,
    "status": "$status",
    "rejections": $rejections,
    "active_restart": $ar_block,
    "last_update": $(date +%s)
}
EOF
    chmod 666 "$STATUS_FILE"
}

init_status() {
    # active_restart.dat ramane neatins daca exista
    local ar_block=$(active_restart_json_block)

    cat > "$STATUS_FILE" <<EOF
{
    "version": $(date +%s),
    "in_progress": false,
    "requester": "null",
    "project": null,
    "requested_at": null,
    "wait_until": null,
    "status": "idle",
    "rejections": [],
    "active_restart": $ar_block,
    "last_update": $(date +%s)
}
EOF
    chmod 666 "$STATUS_FILE"
}

# ============================================================================
# Kill previous WATCHER only
# ============================================================================

kill_previous_watcher() {
    local script_name="$(basename "$0")"
    local pids=$(ps -ef | grep "$script_name.*background_watcher" | grep -v grep | awk '{print $2}')

    if [ -n "$pids" ]; then
        echo "$pids" | while read pid; do
            if [ "$pid" != "$$" ]; then
                kill "$pid" 2>/dev/null
                echo "[$(date)] Killed previous watcher process: $pid" >> "$SCRIPT_DIR/restart.log"
            fi
        done
        sleep 0.5
    fi
}

# ============================================================================
# Background: WATCHER
# ============================================================================

process_watcher_lifecycle() {
    local user="$1"
    local wait_until="$2"
    local requested_at="$3"
    local project="$4"

    # -- 1. WAIT PHASE ------------------------------------------------
    local now=$(date +%s)
    while [ $now -lt $wait_until ]; do
        sleep 1
        now=$(date +%s)

        if [ -f "$STATUS_FILE" ]; then
            local current_json=$(cat "$STATUS_FILE")
            local current_status=$(get_json_field "$current_json" "status")

            if [ "$current_status" = "rejected" ]; then
                echo "[$(date)] Watcher exiting: request was rejected" >> "$SCRIPT_DIR/restart.log"
                exit 0
            fi

            if [ "$current_status" != "pending" ]; then
                exit 0
            fi

            local current_req_at=$(get_json_field "$current_json" "requested_at")
            if [ "$current_req_at" != "$requested_at" ]; then
                echo "[$(date)] Watcher exiting: replaced by newer request" >> "$SCRIPT_DIR/restart.log"
                exit 0
            fi
        fi
    done

    # -- 2. EXECUTE ----------------------------------------------------
    if ! acquire_lock; then
        exit 1
    fi

    local current_json=$(cat "$STATUS_FILE")
    local current_status=$(get_json_field "$current_json" "status")
    local current_req_at=$(get_json_field "$current_json" "requested_at")

    if [ "$current_status" != "pending" ] || [ "$current_req_at" != "$requested_at" ]; then
        release_lock
        echo "[$(date)] Watcher exiting at execution: no longer active" >> "$SCRIPT_DIR/restart.log"
        exit 0
    fi

    # Scrie active restart in .dat
    write_active_restart "$user" "$project" "$(date +%s)" "$requested_at"

    # Actualizeaza statusul
    write_status "executing" "$user" "$project" "$requested_at" "$wait_until" "true" "[]"

    release_lock

    # Lanseaza executor
    nohup "$0" "$user" "background_executor" "$requested_at" "$project" > /dev/null 2>&1 &

    echo "[$(date)] Watcher launched executor for project=$project requested_at=$requested_at" >> "$SCRIPT_DIR/restart.log"
}

# ============================================================================
# Background: EXECUTOR
# ============================================================================

process_executor() {
    local user="$1"
    local original_requested_at="$2"
    local project="$3"

    echo "---------------------------------------------------" >> "$SCRIPT_DIR/restart.log"
    echo "[$(date)] EXECUTOR: Starting restart for project=$project user=$user req_at=$original_requested_at" >> "$SCRIPT_DIR/restart.log"

    # -- ACTUAL RESTART -----------------------------------------------
    if [ -f "$REAL_RESTART_SCRIPT" ]; then
        echo "[$(date)] Executing: $REAL_RESTART_SCRIPT $REAL_RESTART_ARG" >> "$SCRIPT_DIR/restart.log"
        $REAL_RESTART_SCRIPT $REAL_RESTART_ARG >> "$SCRIPT_DIR/restart.log" 2>&1
        EXIT_CODE=$?
        echo "[$(date)] Restart finished with exit code: $EXIT_CODE" >> "$SCRIPT_DIR/restart.log"
    else
        echo "[$(date)] ERROR: Script $REAL_RESTART_SCRIPT not found!" >> "$SCRIPT_DIR/restart.log"
    fi

    # -- COMPLETION ------------------------------------------------
    if ! acquire_lock; then
        echo "[$(date)] EXECUTOR: Could not acquire lock for completion" >> "$SCRIPT_DIR/restart.log"
        return 1
    fi

    # Verificam: suntem inca noi restartul activ?
    local active_req_at=$(ar_requested_at)

    if [ "$active_req_at" != "$original_requested_at" ]; then
        release_lock
        echo "[$(date)] EXECUTOR: A newer restart took over. Skipping completion." >> "$SCRIPT_DIR/restart.log"
        return 0
    fi

    # Suntem inca activi — curatam si marcam completed
    clear_active_restart
    write_status "completed" "$user" "$project" "$original_requested_at" "null" "false" "[]"

    release_lock

    echo "[$(date)] EXECUTOR: Marked as completed for project=$project" >> "$SCRIPT_DIR/restart.log"

    # -- CLEANUP ------------------------------------------------
    sleep 5
    if acquire_lock; then
        local cleanup_status=$(get_json_field "$(cat "$STATUS_FILE")" "status")
        if [ "$cleanup_status" = "completed" ]; then
            init_status
        fi
        release_lock
    fi
}

# ============================================================================
# Start background watcher
# ============================================================================

start_background_watcher() {
    local user="$1"
    local project="$2"
    local now=$(date +%s)
    local wait_until=$((now + APPROVAL_TIMEOUT))

    write_status "pending" "$user" "$project" "$now" "$wait_until" "true" "[]"

    nohup "$0" "$user" "background_watcher" "$wait_until" "$now" "$project" > /dev/null 2>&1 &

    echo "OK:Restart requested for project '$project'. Proceeding in background."
}

# ============================================================================
# REJECT
# ============================================================================

add_rejection() {
    local user="$1"

    if ! acquire_lock; then
        echo "ERROR:Could not acquire lock"
        return 1
    fi

    if [ ! -f "$STATUS_FILE" ]; then
        release_lock
        echo "ERROR:No restart request found"
        return 1
    fi

    local current_json=$(cat "$STATUS_FILE")
    local status=$(get_json_field "$current_json" "status")

    if [ "$status" != "pending" ]; then
        release_lock
        echo "ERROR:Restart is not pending (status: $status)"
        return 1
    fi

    local requester=$(get_json_field "$current_json" "requester")
    local project=$(get_json_field "$current_json" "project")

    if has_active_restart; then
        local prev_requester=$(ar_requester)
        local prev_project=$(ar_project)
        local prev_requested_at=$(ar_requested_at)

        # Rejected, dar active_restart ramane in .dat
        write_status "rejected" "$requester" "$project" "0" "0" "false" \
            "[{\"user\": \"$user\", \"timestamp\": $(date +%s)}]"

        release_lock
        echo "OK:Rejected by $user. Previous restart for '$prev_project' continues."

        # Dupa 5s, restauram executing
        (
            sleep 5
            if acquire_lock 2>/dev/null; then
                local check_status=$(get_json_field "$(cat "$STATUS_FILE")" "status")

                if [ "$check_status" = "rejected" ] && has_active_restart; then
                    write_status "executing" "$prev_requester" "$prev_project" "$prev_requested_at" "null" "true" "[]"
                    echo "[$(date)] Restored status to executing for active restart" >> "$SCRIPT_DIR/restart.log"
                fi
                release_lock
            fi
        ) > /dev/null 2>&1 &

    else
        write_status "rejected" "$requester" "$project" "0" "0" "false" \
            "[{\"user\": \"$user\", \"timestamp\": $(date +%s)}]"

        release_lock
        echo "OK:Rejected by $user"

        (
            sleep 5
            if acquire_lock 2>/dev/null; then
                local cleanup_status=$(get_json_field "$(cat "$STATUS_FILE")" "status")
                if [ "$cleanup_status" = "rejected" ]; then
                    init_status
                fi
                release_lock
            fi
        ) > /dev/null 2>&1 &
    fi
}

# ============================================================================
# MAIN
# ============================================================================

if [ "$ACTION" = "background_watcher" ]; then
    process_watcher_lifecycle "$APP_USERNAME" "$3" "$4" "$5"
    exit 0
fi

if [ "$ACTION" = "background_executor" ]; then
    process_executor "$APP_USERNAME" "$3" "$4"
    exit 0
fi

case "$ACTION" in
    get|check)
        if [ ! -f "$STATUS_FILE" ]; then
            init_status
        fi
        cat "$STATUS_FILE"
        ;;

    request)
        if ! acquire_lock; then
            echo "ERROR:Could not acquire lock"
            exit 1
        fi

        if [ ! -f "$STATUS_FILE" ]; then
            init_status
        fi

        PROJECT_NAME="$3"
        status=$(get_json_field "$(cat "$STATUS_FILE")" "status")

        if [ "$status" = "pending" ]; then
            echo "[$(date)] Overriding pending request, new request from $APP_USERNAME for $PROJECT_NAME" >> "$SCRIPT_DIR/restart.log"
            kill_previous_watcher
        fi

        if [ "$status" = "executing" ]; then
            echo "[$(date)] New request over executing restart, from $APP_USERNAME for $PROJECT_NAME" >> "$SCRIPT_DIR/restart.log"
            kill_previous_watcher
        fi

        start_background_watcher "$APP_USERNAME" "$PROJECT_NAME"
        release_lock
        ;;

    reject)
        add_rejection "$APP_USERNAME"
        ;;

    *)
        echo "Usage: $0 <username> {get|check|request [project]|reject}"
        exit 1
        ;;
esac