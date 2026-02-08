

// ============================================
// INTERNET SPEED MONITOR (OPTIMIZED)
// ============================================

class InternetSpeedMonitor {
    constructor() {
        this.isExpanded = false;
        this.connectionQuality = 'checking';
        this.downloadSpeed = 0;
        this.latency = 0;
        this.autoHideTimer = null;
        this.wasOffline = false; // Track previous offline state

        this.init();
    }

    init() {
        this.createWidget();
        this.startMonitoring();
        this.attachEventListeners();
    }

    createWidget() {
        const widgetHTML = `
            <div id="internetSpeedWidget" class="internet-speed-widget">
                <div class="speed-indicator" id="speedIndicator">
                    <div class="speed-icon">
                        <i class="bi bi-wifi"></i>
                    </div>
                    <div class="speed-status" id="speedStatus">
                        <span class="status-dot"></span>
                    </div>
                </div>
                <div class="speed-details" id="speedDetails">
                    <div class="speed-header">
                        <i class="bi bi-wifi me-2"></i>
                        <span>Connection Status</span>
                    </div>
                    <div class="speed-info">
                        <div class="info-row">
                            <span class="info-label">Quality:</span>
                            <span class="info-value" id="qualityValue">Checking...</span>
                        </div>
                        <div class="info-row">
                            <span class="info-label">Speed:</span>
                            <span class="info-value" id="speedValue">-- Mbps</span>
                        </div>
                        <div class="info-row">
                            <span class="info-label">Latency:</span>
                            <span class="info-value" id="latencyValue">-- ms</span>
                        </div>
                    </div>
                </div>
            </div>

            <style>
                .internet-speed-widget {
                    position: fixed;
                    right: 20px;
                    top: 50%;
                    transform: translateY(-50%);
                    z-index: 9999;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                }

                .speed-indicator {
                    width: 50px;
                    height: 50px;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    cursor: pointer;
                    box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
                    transition: all 0.3s ease;
                    position: relative;
                }

                .speed-indicator:hover {
                    transform: scale(1.1);
                    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.3);
                }

                .speed-icon {
                    color: white;
                    font-size: 24px;
                }

                .speed-status {
                    position: absolute;
                    bottom: 2px;
                    right: 2px;
                }

                .status-dot {
                    width: 12px;
                    height: 12px;
                    border-radius: 50%;
                    background: #4ade80;
                    display: inline-block;
                    border: 2px solid white;
                    animation: pulse 2s infinite;
                }

                @keyframes pulse {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.5; }
                }

                .speed-details {
                    position: absolute;
                    right: 60px;
                    top: 0;
                    background: white;
                    border-radius: 12px;
                    padding: 16px;
                    min-width: 250px;
                    box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
                    opacity: 0;
                    visibility: hidden;
                    transform: translateX(20px);
                    transition: all 0.3s ease;
                }

                .speed-details.show {
                    opacity: 1;
                    visibility: visible;
                    transform: translateX(0);
                }

                .speed-header {
                    font-weight: 600;
                    font-size: 16px;
                    color: #1f2937;
                    margin-bottom: 12px;
                    display: flex;
                    align-items: center;
                }

                .speed-info {
                    display: flex;
                    flex-direction: column;
                    gap: 10px;
                }

                .info-row {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                }

                .info-label {
                    color: #6b7280;
                    font-size: 14px;
                }

                .info-value {
                    color: #1f2937;
                    font-weight: 600;
                    font-size: 14px;
                }

                /* Connection quality colors */
                .quality-excellent { color: #10b981 !important; }
                .quality-good { color: #3b82f6 !important; }
                .quality-fair { color: #f59e0b !important; }
                .quality-poor { color: #ef4444 !important; }
                .quality-offline { color: #6b7280 !important; }

                /* Status dot colors */
                .status-excellent { background: #10b981 !important; }
                .status-good { background: #3b82f6 !important; }
                .status-fair { background: #f59e0b !important; }
                .status-poor { background: #ef4444 !important; }
                .status-offline { background: #6b7280 !important; animation: none; }
            </style>
        `;

        // Insert into the dedicated container
        const container = document.getElementById('internetWidgetContainer');
        if (container) {
            container.innerHTML = widgetHTML;
        } else {
            // Fallback to body if container not found
            document.body.insertAdjacentHTML('beforeend', widgetHTML);
        }
    }

    attachEventListeners() {
        const indicator = document.getElementById('speedIndicator');

        indicator.addEventListener('click', () => {
            this.toggleDetails();
        });

        // Close when clicking outside
        document.addEventListener('click', (e) => {
            const widget = document.getElementById('internetSpeedWidget');
            if (!widget.contains(e.target) && this.isExpanded) {
                this.hideDetails();
            }
        });
    }

    toggleDetails() {
        if (this.isExpanded) {
            this.hideDetails();
        } else {
            this.showDetails();
        }
    }

    showDetails() {
        const details = document.getElementById('speedDetails');
        details.classList.add('show');
        this.isExpanded = true;

        // Auto-hide after 5 seconds
        this.clearAutoHideTimer();
        this.autoHideTimer = setTimeout(() => {
            this.hideDetails();
        }, 5000);
    }

    hideDetails() {
        const details = document.getElementById('speedDetails');
        details.classList.remove('show');
        this.isExpanded = false;
        this.clearAutoHideTimer();
    }

    clearAutoHideTimer() {
        if (this.autoHideTimer) {
            clearTimeout(this.autoHideTimer);
            this.autoHideTimer = null;
        }
    }

    async startMonitoring() {
        // Initial check
        await this.checkConnection();

        // Monitor online/offline events with notifications
        window.addEventListener('online', () => {
            console.log('🌐 Connection restored');
            notifier.success('Internet connection restored!', 3000);
            this.checkConnection();
        });

        window.addEventListener('offline', () => {
            console.log('📡 Connection lost');
            notifier.error('Internet connection lost!', 4000);
            this.updateStatus('offline', 0, 0);
        });

        // Periodic checks every 20 seconds (optimized from 30)
        setInterval(() => {
            if (navigator.onLine) {
                this.checkConnection();
            }
        }, 20000);
    }

    async checkConnection() {
        if (!navigator.onLine) {
            this.updateStatus('offline', 0, 0);
            return;
        }

        try {
            const startTime = performance.now();

            // Ping test using a small resource
            const response = await fetch('/favicon.ico?t=' + Date.now(), {
                method: 'GET',
                cache: 'no-cache'
            });

            const endTime = performance.now();
            const latency = Math.round(endTime - startTime);

            // Estimate speed based on latency and connection type
            const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
            let estimatedSpeed = 0;

            if (connection && connection.downlink) {
                estimatedSpeed = connection.downlink;
            } else {
                // Estimate based on latency
                if (latency < 50) estimatedSpeed = 50;
                else if (latency < 100) estimatedSpeed = 25;
                else if (latency < 200) estimatedSpeed = 10;
                else if (latency < 500) estimatedSpeed = 5;
                else estimatedSpeed = 1;
            }

            // Determine quality
            let quality;
            if (latency < 50 && estimatedSpeed > 20) quality = 'excellent';
            else if (latency < 100 && estimatedSpeed > 10) quality = 'good';
            else if (latency < 200 && estimatedSpeed > 5) quality = 'fair';
            else quality = 'poor';

            this.updateStatus(quality, estimatedSpeed, latency);

            // If was offline and now online, show reconnection message
            if (this.wasOffline) {
                notifier.success('Connection quality: ' + quality.charAt(0).toUpperCase() + quality.slice(1), 3000);
                this.wasOffline = false;
            }

        } catch (error) {
            console.error('Connection check failed:', error);
            this.updateStatus('poor', 0, 999);
        }
    }

    updateStatus(quality, speed, latency) {
        this.connectionQuality = quality;
        this.downloadSpeed = speed;
        this.latency = latency;

        // Track offline state
        if (quality === 'offline') {
            this.wasOffline = true;
        }

        const statusDot = document.querySelector('.status-dot');
        const qualityValue = document.getElementById('qualityValue');
        const speedValue = document.getElementById('speedValue');
        const latencyValue = document.getElementById('latencyValue');

        if (!statusDot || !qualityValue || !speedValue || !latencyValue) return;

        // Update status dot color
        statusDot.className = 'status-dot status-' + quality;

        // Update quality text
        const qualityText = {
            'excellent': 'Excellent',
            'good': 'Good',
            'fair': 'Fair',
            'poor': 'Poor',
            'offline': 'Offline',
            'checking': 'Checking...'
        };

        qualityValue.textContent = qualityText[quality] || 'Unknown';
        qualityValue.className = 'info-value quality-' + quality;

        // Update speed
        if (quality === 'offline') {
            speedValue.textContent = 'No Connection';
            latencyValue.textContent = '--';
        } else {
            speedValue.textContent = speed > 0 ? `~${speed.toFixed(1)} Mbps` : 'Checking...';
            latencyValue.textContent = latency > 0 ? `${latency} ms` : '--';
        }
    }
}
// Toast Notification System using Bootstrap Toast
function showToast(message, type = 'info') {
    console.log('showToast called:', message, type);
    const container = document.getElementById('toastContainer');
    if (!container) {
        console.error('Toast container not found!');
        return;
    }

    const icons = {
        success: '<i class="bi bi-check-circle-fill"></i>',
        error: '<i class="bi bi-x-circle-fill"></i>',
        warning: '<i class="bi bi-exclamation-triangle-fill"></i>',
        info: '<i class="bi bi-info-circle-fill"></i>'
    };

    const bgColors = {
        success: 'bg-success',
        error: 'bg-danger',
        warning: 'bg-warning',
        info: 'bg-info'
    };

    const toastEl = document.createElement('div');
    toastEl.className = 'toast align-items-center text-white border-0';
    toastEl.classList.add(bgColors[type]);
    toastEl.setAttribute('role', 'alert');
    toastEl.setAttribute('aria-live', 'assertive');
    toastEl.setAttribute('aria-atomic', 'true');

    toastEl.innerHTML = `
        <div class="d-flex">
            <div class="toast-body">
                ${icons[type]} ${message}
            </div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
        </div>
    `;

    container.appendChild(toastEl);
    const toast = new bootstrap.Toast(toastEl, { delay: 4000 });
    toast.show();

    toastEl.addEventListener('hidden.bs.toast', function () {
        toastEl.remove();
    });
}

// Check for server messages on page load
window.addEventListener('DOMContentLoaded', function() {
    new InternetSpeedMonitor();

    console.log('DOM Loaded - Checking for messages...');
    console.log('Toast container exists:', !!document.getElementById('toastContainer'));

    const noDataElement = document.getElementById('noDataMessage');
    console.log('noDataMessage element:', noDataElement);
    console.log('noDataMessage content:', noDataElement ? noDataElement.textContent : 'N/A');
    if (noDataElement && noDataElement.textContent.trim() !== '') {
        console.log('Showing noData toast:', noDataElement.textContent);
        showToast(noDataElement.textContent, 'info');
    }

    const successElement = document.getElementById('successMessage');
    console.log('successMessage element:', successElement);
    console.log('successMessage content:', successElement ? successElement.textContent : 'N/A');
    if (successElement && successElement.textContent.trim() !== '') {
        console.log('Showing success toast:', successElement.textContent);
        showToast(successElement.textContent, 'success');
    }

    const errorElement = document.getElementById('errorMessage');
    console.log('errorMessage element:', errorElement);
    console.log('errorMessage content:', errorElement ? errorElement.textContent : 'N/A');
    if (errorElement && errorElement.textContent.trim() !== '') {
        console.log('Showing error toast:', errorElement.textContent);
        showToast(errorElement.textContent, 'error');
    }

    const noRequestDataElement = document.getElementById('noRequestDataMessage');
    console.log('noRequestDataMessage element:', noRequestDataElement);
    console.log('noRequestDataMessage content:', noRequestDataElement ? noRequestDataElement.textContent : 'N/A');
    if (noRequestDataElement && noRequestDataElement.textContent.trim() !== '') {
        console.log('Showing noRequestData toast:', noRequestDataElement.textContent);
        showToast(noRequestDataElement.textContent, 'info');
    }
});

// Message Modal Functions
function openMessageModal(button) {
    const message = button.getAttribute('data-message');
    document.getElementById('messageContent').innerHTML = message.replace(/\n/g, '<br>');
}

// Confess Update Modal Functions
function openConfessUpdateModal(button) {
    // Set all hidden fields
    document.getElementById('confessRoomHash').value = button.getAttribute('data-roomhash') || '';
    document.getElementById('confessInitiatedBy').value = button.getAttribute('data-initiatedby') || '';
    document.getElementById('confessSenderName').value = button.getAttribute('data-sendername') || '';
    document.getElementById('confessSenderEmail').value = button.getAttribute('data-senderemail') || '';
    document.getElementById('confessRoomName').value = button.getAttribute('data-roomname') || '';
    document.getElementById('confessReceiverAlias').value = button.getAttribute('data-receiveralias') || '';
    document.getElementById('confessType').value = button.getAttribute('data-confesstype') || '';
    document.getElementById('confessEmail').value = button.getAttribute('data-email') || '';
    document.getElementById('confessSongName').value = button.getAttribute('data-songname') || '';
    document.getElementById('confessSingerName').value = button.getAttribute('data-singername') || '';
    document.getElementById('confessMessage').value = button.getAttribute('data-message') || '';

    // Set display fields
    document.getElementById('displayInitiatedBy').textContent = button.getAttribute('data-initiatedby') || '';
    document.getElementById('displaySenderName').textContent = button.getAttribute('data-sendername') || '';
    document.getElementById('displaySenderEmail').textContent = button.getAttribute('data-senderemail') || '';
    document.getElementById('displayRoomName').textContent = button.getAttribute('data-roomname') || '';
    document.getElementById('displayReceiverAlias').textContent = button.getAttribute('data-receiveralias') || '';
    document.getElementById('displayConfessType').textContent = button.getAttribute('data-confesstype') || '';
    document.getElementById('displayEmail').textContent = button.getAttribute('data-email') || '';
    document.getElementById('displayConfessSongName').textContent = button.getAttribute('data-songname') || '';
    document.getElementById('displaySingerName').textContent = button.getAttribute('data-singername') || '';

    const status = button.getAttribute('data-status');
    const note = button.getAttribute('data-note');

    document.getElementById('confessStatus').value = '';
    document.getElementById('confessNote').value = note && note !== 'null' ? note : '';

    // Reset validation states
    document.getElementById('confessNote').classList.remove('is-invalid');
    document.getElementById('noteRequiredIndicator').style.display = 'none';
}

// Song Update Modal Functions
function openSongUpdateModal(button) {
    const requestor = button.getAttribute('data-requestor');
    const songName = button.getAttribute('data-songname');
    const movieName = button.getAttribute('data-moviename');
    const singerName = button.getAttribute('data-singername');
    const status = button.getAttribute('data-status');
    const note = button.getAttribute('data-note');

    // Set hidden fields
    document.getElementById('songRequestor').value = requestor;
    document.getElementById('songMovieName').value = movieName || '';
    document.getElementById('songSingerName').value = singerName;

    // Set display fields
    document.getElementById('displayRequestor').textContent = requestor;
    document.getElementById('songName').value = songName;
    document.getElementById('displayMovieName').textContent = movieName || 'N/A';
    document.getElementById('displaySongSingerName').textContent = singerName;
    document.getElementById('songStatus').value = status;
    document.getElementById('songNote').value = note && note !== 'null' ? note : '';
}

// Toggle note required based on status selection
function toggleNoteRequired(selectElement) {
    const noteField = document.getElementById('confessNote');
    const noteIndicator = document.getElementById('noteRequiredIndicator');

    if (selectElement.value === 'REJECTED') {
        noteField.setAttribute('required', 'required');
        noteIndicator.style.display = 'inline';
    } else {
        noteField.removeAttribute('required');
        noteIndicator.style.display = 'none';
        noteField.classList.remove('is-invalid');
    }
}

// Form validation for confess update
document.getElementById('confessUpdateForm').addEventListener('submit', function(e) {
    const status = document.getElementById('confessStatus').value;
    const note = document.getElementById('confessNote').value.trim();
    const noteField = document.getElementById('confessNote');

    if (status === 'REJECTED' && note === '') {
        e.preventDefault();
        noteField.classList.add('is-invalid');
        showToast('Please provide a reason when rejecting a request.', 'error');
        return false;
    }

    noteField.classList.remove('is-invalid');
    return true;
});

// Clean up modal content when closed
document.getElementById('messageModal').addEventListener('hidden.bs.modal', function () {
    document.getElementById('messageContent').innerHTML = '';
});

document.getElementById('confessUpdateModal').addEventListener('hidden.bs.modal', function () {
    document.getElementById('confessUpdateForm').reset();
    document.getElementById('displayInitiatedBy').textContent = '';
    document.getElementById('displaySenderName').textContent = '';
    document.getElementById('displaySenderEmail').textContent = '';
    document.getElementById('displayRoomName').textContent = '';
    document.getElementById('displayReceiverAlias').textContent = '';
    document.getElementById('displayConfessType').textContent = '';
    document.getElementById('displayEmail').textContent = '';
    document.getElementById('displayConfessSongName').textContent = '';
    document.getElementById('displaySingerName').textContent = '';
    document.getElementById('confessNote').classList.remove('is-invalid');
    document.getElementById('noteRequiredIndicator').style.display = 'none';
});

document.getElementById('songUpdateModal').addEventListener('hidden.bs.modal', function () {
    document.getElementById('songUpdateForm').reset();
    document.getElementById('displayRequestor').textContent = '';
    document.getElementById('displayMovieName').textContent = '';
    document.getElementById('displaySongSingerName').textContent = '';
});