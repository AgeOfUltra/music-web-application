#!/bin/bash
# ============================================
# Docker Log Export Script - Ubuntu
# ============================================

BACKUP_DIR="/home/ubuntu/docker-logs-backup"
DATE=$(date +%Y-%m-%d_%H-%M-%S)
CONTAINER_NAME="music-web-application"

# Create backup directory
mkdir -p "$BACKUP_DIR"

# Export Docker logs
echo "[$DATE] Exporting Docker logs..."
docker logs "$CONTAINER_NAME" > "$BACKUP_DIR/docker-logs-$DATE.log" 2>&1

if [ $? -eq 0 ]; then
    echo "[$DATE] Logs exported successfully to: $BACKUP_DIR/docker-logs-$DATE.log"

    # Compress the log file
    gzip "$BACKUP_DIR/docker-logs-$DATE.log"
    echo "[$DATE] Log file compressed"
else
    echo "[$DATE] ERROR: Failed to export logs"
    exit 1
fi

# Delete logs older than 7 days
find "$BACKUP_DIR" -name "docker-logs-*.log.gz" -mtime +7 -delete
echo "[$DATE] Cleanup complete - deleted logs older than 7 days"