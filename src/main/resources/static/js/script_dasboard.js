const DEBUG = false; // Set to false to disable all console.logs

if (!DEBUG) {
    console.log = function() {};
}
// Bootstrap Toast notification system
class ToastNotifier {
    constructor() {
        this.container = document.getElementById('toastContainer');
    }

    show(message, type = 'error', duration = 5000) {
        const toastId = 'toast-' + Date.now();
        const bgClass = type === 'success' ? 'bg-success' : 'bg-danger';
        const icon = type === 'success' ? 'bi-check-circle-fill' : 'bi-exclamation-circle-fill';

        const toastHtml = `
            <div id="${toastId}" class="toast align-items-center text-white ${bgClass} border-0" role="alert" aria-live="assertive" aria-atomic="true">
                <div class="d-flex">
                    <div class="toast-body d-flex align-items-center">
                        <i class="bi ${icon} me-2 fs-5"></i>
                        <span>${message}</span>
                    </div>
                    <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
                </div>
            </div>
        `;

        this.container.insertAdjacentHTML('beforeend', toastHtml);

        const toastElement = document.getElementById(toastId);
        const bsToast = new bootstrap.Toast(toastElement, { delay: duration });
        bsToast.show();

        toastElement.addEventListener('hidden.bs.toast', () => {
            toastElement.remove();
        });

        return toastElement;
    }

    success(message, duration = 4000) {
        return this.show(message, 'success', duration);
    }

    error(message, duration = 5000) {
        return this.show(message, 'error', duration);
    }

    info(message, duration = 4000) {
        const toastId = 'toast-' + Date.now();
        const toastHtml = `
            <div id="${toastId}" class="toast align-items-center text-white bg-info border-0" role="alert" aria-live="assertive" aria-atomic="true">
                <div class="d-flex">
                    <div class="toast-body d-flex align-items-center">
                        <i class="bi bi-info-circle-fill me-2 fs-5"></i>
                        <span>${message}</span>
                    </div>
                    <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
                </div>
            </div>
        `;

        this.container.insertAdjacentHTML('beforeend', toastHtml);

        const toastElement = document.getElementById(toastId);
        const bsToast = new bootstrap.Toast(toastElement, { delay: duration });
        bsToast.show();

        toastElement.addEventListener('hidden.bs.toast', () => {
            toastElement.remove();
        });

        return toastElement;
    }
}

// Initialize notifier
const notifier = new ToastNotifier();

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
            const response = await fetch('/public/internal/speed/test?ts=' + Date.now(), {
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

// ============================================
// PREVENT BROWSER BACK AND FORWARD NAVIGATION
// ============================================

(function preventBrowserNavigation() {
    // Track if user is intentionally navigating away
    let allowNavigation = false;

    console.log('🔒 Dashboard navigation prevention initialized');

    // Check if current page is dashboard.html
    function isDashboardPage() {
        return window.location.pathname.includes('dashboard.html') ||
            window.location.pathname.includes('/dashboard') ||
            window.location.pathname.endsWith('/dashboard');
    }

    // ============================================
    // METHOD 1: HISTORY MANIPULATION
    // ============================================
    function setupHistoryPrevention() {
        // Replace current state
        window.history.replaceState(
            { page: 'dashboard', preventNav: true },
            document.title,
            window.location.href
        );

        // Push a dummy state to create a barrier
        window.history.pushState(
            { page: 'dashboard-barrier', preventNav: true },
            document.title,
            window.location.href
        );

        // Listen for popstate event (triggered when back/forward button is clicked)
        window.addEventListener('popstate', function(event) {
            if (!allowNavigation) {

                // ============================================
                // BACK NAVIGATION → AUTO LOGOUT
                // ============================================
                // When user clicks the browser back button, we detect it here
                // If the state is NOT the barrier state, it means they're trying to go back
                // This will IMMEDIATELY log them out without showing any notification
                if (event.state && event.state.page !== 'dashboard-barrier') {
                    console.log('⬅️ Back button pressed - Auto logout');

                    // LOGOUT HAPPENS HERE - Redirect to logout endpoint
                    window.location.href = '/app/music/public/logout';
                    return;
                }

                // ============================================
                // FORWARD NAVIGATION → SILENT BLOCK (dashboard.html only)
                // ============================================
                // When user clicks the browser forward button on dashboard.html
                // We SILENTLY prevent them from going forward (to chat.html)
                // No notification is shown, navigation is just blocked
                if (isDashboardPage()) {
                    // Push two states to create a strong barrier against forward navigation
                    window.history.pushState(
                        { page: 'dashboard-barrier', preventNav: true },
                        document.title,
                        window.location.href
                    );

                    window.history.pushState(
                        { page: 'dashboard-current', preventNav: true },
                        document.title,
                        window.location.href
                    );

                    console.log('➡️ Forward navigation blocked silently on dashboard');
                    // NO NOTIFICATION - Silent blocking to prevent navigation to chat.html
                }
            }
        });
    }

    // ============================================
    // METHOD 2: KEYBOARD SHORTCUTS PREVENTION
    // ============================================
    function preventKeyboardNavigation() {
        document.addEventListener('keydown', function(e) {

            // Prevent Alt+Left (back) → Auto logout
            if (e.altKey && e.key === 'ArrowLeft') {
                if (!allowNavigation) {
                    e.preventDefault();
                    console.log('⌨️ Alt+Left pressed - Auto logout');
                    // LOGOUT via keyboard shortcut
                    window.location.href = '/app/music/public/logout';
                }
            }

            // Prevent Alt+Right (forward) on dashboard.html → Block silently
            if (e.altKey && e.key === 'ArrowRight') {
                if (!allowNavigation && isDashboardPage()) {
                    e.preventDefault();
                    console.log('⌨️ Alt+Right blocked silently on dashboard');
                    // SILENT BLOCK - No notification, no navigation to chat.html
                }
            }

            // Prevent Backspace navigation (when not in input field) → Auto logout
            if (e.key === 'Backspace' &&
                e.target.tagName !== 'INPUT' &&
                e.target.tagName !== 'TEXTAREA' &&
                !e.target.isContentEditable) {
                if (!allowNavigation) {
                    e.preventDefault();
                    console.log('⌫ Backspace pressed - Auto logout');
                    // LOGOUT via backspace
                    window.location.href = '/app/music/public/logout';
                }
            }
        });
    }

    // ============================================
    // METHOD 3: MOUSE BUTTONS PREVENTION
    // ============================================
    function preventMouseNavigation() {
        document.addEventListener('mousedown', function(e) {

            // Mouse button 3 = back → Auto logout
            if (e.button === 3 && !allowNavigation) {
                e.preventDefault();
                console.log('🖱️ Mouse back button pressed - Auto logout');
                // LOGOUT via mouse back button
                window.location.href = '/app/music/public/logout';
            }

            // Mouse button 4 = forward on dashboard.html → Block silently
            if (e.button === 4 && !allowNavigation && isDashboardPage()) {
                e.preventDefault();
                console.log('🖱️ Mouse forward button blocked silently on dashboard');
                // SILENT BLOCK - No notification, prevents navigation to chat.html
            }
        });
    }

    // Method 4: Allow navigation when user explicitly logs out or navigates
    function setupNavigationWhitelist() {
        // Allow navigation when logout is clicked
        const logoutLinks = document.querySelectorAll('a[href*="logout"], button[onclick*="logout"]');
        logoutLinks.forEach(link => {
            link.addEventListener('click', function() {
                allowNavigation = true;
                console.log('✅ Logout clicked - navigation allowed');
            });
        });

        // Allow navigation when clicking on legitimate navigation links
        document.addEventListener('click', function(e) {
            const link = e.target.closest('a');
            if (link && link.href) {
                // Allow navigation for specific internal links
                const allowedPaths = [
                    '/logout',
                    '/app/music/public/logout',
                    '/profile',
                    '/settings',
                    '/app/music/nodes/confess'
                ];

                const shouldAllow = allowedPaths.some(path => link.href.includes(path));

                if (shouldAllow || link.classList.contains('allow-navigation')) {
                    allowNavigation = true;
                    console.log('✅ Legitimate navigation link clicked - allowed');
                }
            }
        });

        // Allow navigation when forms are submitted
        document.addEventListener('submit', function(e) {
            // Don't allow navigation for modal forms that stay on the same page
            const form = e.target;
            if (!form.classList.contains('prevent-navigation')) {
                allowNavigation = true;
                console.log('✅ Form submitted - navigation allowed');
            }
        });
    }

    // Initialize all prevention methods
    setupHistoryPrevention();
    preventKeyboardNavigation();
    preventMouseNavigation();
    setupNavigationWhitelist();

    console.log('✅ Dashboard navigation prevention active');
    console.log('📋 Back navigation → Auto logout (NO notification)');
    console.log('📋 Forward navigation → Silently blocked on dashboard.html (prevents chat.html navigation)');
})();

// ============================================
// END PREVENT BROWSER NAVIGATION
// ============================================

// Song Autocomplete System
class SongAutocomplete {
    constructor() {
        this.songInput = document.getElementById('songName');
        this.suggestionsContainer = document.getElementById('songSuggestions');
        this.cachedSongs = [];
        this.selectedIndex = -1;

        this.init();
    }

    async init() {
        if (!this.songInput || !this.suggestionsContainer) return;

        await this.fetchCachedSongs();

        this.songInput.addEventListener('input', (e) => this.handleInput(e));
        this.songInput.addEventListener('keydown', (e) => this.handleKeydown(e));

        document.addEventListener('click', (e) => {
            if (!this.songInput.contains(e.target) && !this.suggestionsContainer.contains(e.target)) {
                this.closeSuggestions();
            }
        });
    }

    async fetchCachedSongs() {
        try {
            const response = await fetch('/app/music/audio/getAllCachedSongs');
            if (response.ok) {
                this.cachedSongs = await response.json();
                console.log('Cached songs loaded:', this.cachedSongs.length);
            } else {
                console.error('Failed to fetch cached songs');
                this.cachedSongs = [];
            }
        } catch (error) {
            console.error('Error fetching cached songs:', error);
            this.cachedSongs = [];
        }
    }

    handleInput(e) {
        const searchTerm = e.target.value.trim();

        if (searchTerm.length === 0) {
            this.closeSuggestions();
            return;
        }

        const filteredSongs = this.filterSongs(searchTerm);
        this.showSuggestions(filteredSongs);
    }

    filterSongs(searchTerm) {
        const term = searchTerm.toLowerCase();
        return this.cachedSongs.filter(song =>
            song.toLowerCase().includes(term)
        ).slice(0, 10);
    }

    showSuggestions(songs) {
        this.selectedIndex = -1;

        if (songs.length === 0) {
            this.suggestionsContainer.innerHTML = '<div class="list-group-item text-muted fst-italic">No matching songs found</div>';
            this.suggestionsContainer.style.display = 'block';
            return;
        }

        this.suggestionsContainer.innerHTML = songs.map((song, index) =>
            `<a href="#" class="list-group-item list-group-item-action" data-index="${index}" data-song="${this.escapeHtml(song)}">
                ${this.highlightMatch(song, this.songInput.value)}
            </a>`
        ).join('');

        this.suggestionsContainer.querySelectorAll('.list-group-item').forEach(item => {
            item.addEventListener('click', (e) => {
                e.preventDefault();
                const songName = e.currentTarget.getAttribute('data-song');
                this.selectSong(songName);
            });
        });

        this.suggestionsContainer.style.display = 'block';
    }

    highlightMatch(text, searchTerm) {
        const regex = new RegExp(`(${this.escapeRegex(searchTerm)})`, 'gi');
        return this.escapeHtml(text).replace(regex, '<strong>$1</strong>');
    }

    handleKeydown(e) {
        if (this.suggestionsContainer.style.display !== 'block') return;

        const items = this.suggestionsContainer.querySelectorAll('.list-group-item');

        switch(e.key) {
            case 'ArrowDown':
                e.preventDefault();
                this.selectedIndex = Math.min(this.selectedIndex + 1, items.length - 1);
                this.updateSelection(items);
                break;

            case 'ArrowUp':
                e.preventDefault();
                this.selectedIndex = Math.max(this.selectedIndex - 1, -1);
                this.updateSelection(items);
                break;

            case 'Enter':
                e.preventDefault();
                if (this.selectedIndex >= 0 && items[this.selectedIndex]) {
                    const songName = items[this.selectedIndex].getAttribute('data-song');
                    this.selectSong(songName);
                }
                break;

            case 'Escape':
                this.closeSuggestions();
                break;
        }
    }

    updateSelection(items) {
        items.forEach((item, index) => {
            if (index === this.selectedIndex) {
                item.classList.add('active');
                item.scrollIntoView({ block: 'nearest' });
            } else {
                item.classList.remove('active');
            }
        });
    }

    selectSong(songName) {
        this.songInput.value = songName;
        this.closeSuggestions();
        this.songInput.focus();
    }

    closeSuggestions() {
        this.suggestionsContainer.style.display = 'none';
        this.selectedIndex = -1;
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    escapeRegex(text) {
        return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }
}

// Toggle other confess type input
function toggleOtherInput() {
    const confessType = document.getElementById('confessType').value;
    const otherDiv = document.getElementById('otherConfessTypeDiv');
    const otherInput = document.getElementById('otherConfessType');

    if (confessType === 'other') {
        otherDiv.style.display = 'block';
        otherInput.required = true;
    } else {
        otherDiv.style.display = 'none';
        otherInput.required = false;
        otherInput.value = '';
    }
}

// Function to clear confess form fields
function clearConfessForm() {
    const confessForm = document.getElementById('sendConfessForm');
    if (confessForm) {
        // Reset the form first (this clears all Thymeleaf-bound fields)
        confessForm.reset();

        // Then manually clear each field to ensure they're empty
        const senderOriginalName = document.getElementById('senderOriginalName');
        const confessRoomName = document.getElementById('confessRoomName');
        const receiverAlias = document.getElementById('receiverAlias');
        const confessType = document.getElementById('confessType');
        const otherConfessType = document.getElementById('otherConfessType');
        const receiverEmail = document.getElementById('receiverEmail');
        const songName = document.getElementById('songName');
        const singerName = document.getElementById('singerName');
        const confessMessage = document.getElementById('confessMessage');

        if (senderOriginalName) senderOriginalName.value = '';
        if (confessRoomName) confessRoomName.value = '';
        if (receiverAlias) receiverAlias.value = '';
        if (confessType) confessType.value = '';
        if (otherConfessType) otherConfessType.value = '';
        if (receiverEmail) receiverEmail.value = '';
        if (songName) songName.value = '';
        if (singerName) singerName.value = '';
        if (confessMessage) confessMessage.value = '';

        // Hide the other confess type div
        const otherDiv = document.getElementById('otherConfessTypeDiv');
        if (otherDiv) {
            otherDiv.style.display = 'none';
        }

        console.log('✅ Confess form cleared successfully');
    }
}

// Function to clear request song form fields
function clearRequestSongForm() {
    const requestSongForm = document.getElementById('requestSongForm');
    if (requestSongForm) {
        // Reset the form first
        requestSongForm.reset();

        // Then manually clear each field
        const requestSongNameField = document.getElementById('requestSongName');
        const requestMovieNameField = document.getElementById('requestMovieName');
        const requestSingerNameField = document.getElementById('requestSingerName');

        if (requestSongNameField) requestSongNameField.value = '';
        if (requestMovieNameField) requestMovieNameField.value = '';
        if (requestSingerNameField) requestSingerNameField.value = '';

        console.log('✅ Request song form cleared successfully');
    }
}

// ============================================
// CONSOLIDATED DOM CONTENT LOADED
// ============================================
window.addEventListener('DOMContentLoaded', function() {
    // Initialize song autocomplete
    new SongAutocomplete();

    // Initialize Internet Speed Monitor
    new InternetSpeedMonitor();

    // ============================================
    // ALPHANUMERIC VALIDATION FOR ROOM NAMES
    // ============================================
    addAlphanumericValidation('roomName');
    addAlphanumericValidation('confessRoomName');

    // ============================================
    // MESSAGE LENGTH VALIDATION (100 words minimum)
    // ============================================
    const confessMessage = document.getElementById('confessMessage');
    if (confessMessage) {
        confessMessage.addEventListener('blur', function() {
            const wordCount = this.value.trim().split(/\s+/).filter(word => word.length > 0).length;
            if (this.value.trim() && wordCount < 100) {
                notifier.error(`Message must be at least 100 words. Current: ${wordCount} words`);
            }
        });
    }

    // ============================================
    // JOIN ROOM - PASTE/MANUAL INPUT HANDLING
    // ============================================
    const pasteMethod = document.getElementById('pasteMethod');
    const manualMethod = document.getElementById('manualMethod');
    const pasteSection = document.getElementById('pasteSection');
    const manualSection = document.getElementById('manualSection');
    const roomDetailsPaste = document.getElementById('roomDetailsPaste');
    const extractedInfo = document.getElementById('extractedInfo');

    // Toggle sections
    pasteMethod?.addEventListener('change', () => {
        pasteSection.classList.remove('d-none');
        manualSection.classList.add('d-none');
    });

    manualMethod?.addEventListener('change', () => {
        pasteSection.classList.add('d-none');
        manualSection.classList.remove('d-none');
        extractedInfo.classList.add('d-none');
    });

    // Auto-extract from paste
    roomDetailsPaste?.addEventListener('input', function() {
        const text = this.value;
        const roomCode = text.match(/Room\s*Code\s*:\s*(\S+)/i)?.[1];
        const passcode = text.match(/Passcode\s*:\s*(\S+)/i)?.[1];

        if (roomCode && passcode) {
            document.getElementById('extractedRoomCode').textContent = roomCode;
            document.getElementById('extractedPasscode').textContent = passcode;
            document.getElementById('joinRoomName').value = roomCode;
            document.getElementById('joinPassCode').value = passcode;
            extractedInfo.classList.remove('d-none');
        } else {
            extractedInfo.classList.add('d-none');
        }
    });

    // Manual input sync
    document.getElementById('manualRoomCode')?.addEventListener('input', function() {
        document.getElementById('joinRoomName').value = this.value;
    });

    document.getElementById('manualPasscode')?.addEventListener('input', function() {
        document.getElementById('joinPassCode').value = this.value;
    });

    // ============================================
    // FORM ERROR AND SUCCESS HANDLING
    // ============================================
    let hasErrors = false;
    let hasConfessErrors = false;
    let hasRequestSongErrors = false;
    let hasConfessSuccess = false;
    let hasRequestSongSuccess = false;

    // Get Bootstrap modal instances
    const createModal = new bootstrap.Modal(document.getElementById('createModal'));
    const joinModal = new bootstrap.Modal(document.getElementById('joinModal'));
    const confessModal = new bootstrap.Modal(document.getElementById('sendConfess'));
    const requestSongModal = new bootstrap.Modal(document.getElementById('requestSongModal'));

    // CREATE ROOM - FIELD VALIDATION ERRORS
    const fieldErrorsContainer = document.getElementById('fieldErrors');
    if (fieldErrorsContainer) {
        const errorItems = fieldErrorsContainer.querySelectorAll('.error-item');
        errorItems.forEach(item => {
            const errorMessage = item.textContent.trim();
            if (errorMessage) {
                notifier.error(errorMessage);
                hasErrors = true;
            }
        });
    }

    // CONFESS FORM - FIELD VALIDATION ERRORS
    const confessFieldErrorsContainer = document.getElementById('confessFieldErrors');
    if (confessFieldErrorsContainer) {
        const errorItems = confessFieldErrorsContainer.querySelectorAll('.error-item');
        errorItems.forEach(item => {
            const errorMessage = item.textContent.trim();
            if (errorMessage) {
                notifier.error(errorMessage);
                hasConfessErrors = true;
            }
        });
    }

    // REQUEST SONG - FIELD VALIDATION ERRORS
    const requestSongFieldErrorsContainer = document.getElementById('requestSongFieldErrors');
    if (requestSongFieldErrorsContainer) {
        const errorItems = requestSongFieldErrorsContainer.querySelectorAll('.error-item');
        errorItems.forEach(item => {
            const errorMessage = item.textContent.trim();
            if (errorMessage) {
                notifier.error(errorMessage);
                hasRequestSongErrors = true;
            }
        });
    }

    // REQUEST SONG - ERROR/SUCCESS FLASH MESSAGE
    const requestSongErrorContainer = document.getElementById('requestSongError');
    if (requestSongErrorContainer) {
        const requestSongError = requestSongErrorContainer.textContent.trim();
        if (requestSongError) {
            if (requestSongError.toLowerCase().includes('success') ||
                requestSongError.toLowerCase().includes('submitted') ||
                requestSongError.toLowerCase().includes('received')) {
                notifier.success(requestSongError);
                hasRequestSongSuccess = true;
                clearRequestSongForm();
            } else {
                notifier.error(requestSongError);
                hasRequestSongErrors = true;
            }
        }
    }

    // CREATE ROOM - CREATION ERROR
    const creationErrorContainer = document.getElementById('creationError');
    if (creationErrorContainer) {
        const creationError = creationErrorContainer.textContent.trim();
        if (creationError) {
            notifier.error(creationError);
            createModal.show();
            hasErrors = true;
        }
    }

    // JOIN ROOM - JOIN ERROR
    const joinErrorContainer = document.getElementById('joinError');
    if (joinErrorContainer) {
        const joinError = joinErrorContainer.textContent.trim();
        if (joinError) {
            notifier.error(joinError);
            joinModal.show();
            hasErrors = true;
        }
    }

    // CONFESS FORM - EMAIL STATUS (SUCCESS/ERROR)
    const emailStatusElement = document.getElementById('emailStatus');
    if (emailStatusElement) {
        const emailStatus = emailStatusElement.textContent.trim();
        if (emailStatus) {
            if (emailStatus.toLowerCase().includes('success') ||
                emailStatus.toLowerCase().includes('sent') ||
                emailStatus.toLowerCase().includes('submitted')) {
                notifier.success(emailStatus);
                hasConfessSuccess = true;
                clearConfessForm();
            } else {
                notifier.error(emailStatus);
                hasConfessErrors = true;
            }
        }
    }

    // OPEN MODALS IF THERE ARE ERRORS (NOT SUCCESS)
    if (hasConfessErrors && !hasConfessSuccess) {
        confessModal.show();
    }

    if (hasRequestSongErrors && !hasRequestSongSuccess) {
        requestSongModal.show();
    }


    // Initialize Bootstrap tooltips
    const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl);
    });
});

// ============================================
// LOGOUT FUNCTION
// ============================================
function dashboardLogout() {
    // Allow navigation for logout
    window.allowNavigation = true;
    window.location.href = '/app/music/public/logout';
}

// ============================================
// ALPHANUMERIC VALIDATION FOR ROOM NAMES
// ============================================
function addAlphanumericValidation(inputId) {
    const input = document.getElementById(inputId);
    if (!input) return;

    const errorMessage = input.nextElementSibling;

    // Prevent non-alphanumeric characters from being typed
    input.addEventListener('keydown', function(e) {
        // Allow control keys
        const controlKeys = ['Backspace', 'Delete', 'ArrowLeft', 'ArrowRight', 'Tab'];
        if (controlKeys.includes(e.key)) return;

        // Block space
        if (e.key === ' ' || e.keyCode === 32) {
            e.preventDefault();
            return;
        }

        // Block special characters (allow only a-z, A-Z, 0-9)
        if (!/^[a-zA-Z0-9]$/.test(e.key)) {
            e.preventDefault();
        }
    });

    // Remove invalid characters if pasted
    input.addEventListener('input', function(e) {
        const invalidChars = /[^a-zA-Z0-9]/g;
        if (invalidChars.test(this.value)) {
            this.value = this.value.replace(invalidChars, '');
            if (errorMessage) {
                errorMessage.style.display = 'block';
                setTimeout(() => {
                    errorMessage.style.display = 'none';
                }, 2000);
            }
        }
    });

    // Clean on blur
    input.addEventListener('blur', function() {
        this.value = this.value.replace(/[^a-zA-Z0-9]/g, '');
    });
}
// ============================================
// MOBILE HAMBURGER MENU
// ============================================

(function setupMobileMenu() {
    const hamburgerToggle = document.getElementById('hamburgerToggle');
    const mobileMenuOverlay = document.getElementById('mobileMenuOverlay');
    const closeMenu = document.getElementById('closeMenu');

    if (!hamburgerToggle || !mobileMenuOverlay || !closeMenu) {
        console.log('Mobile menu elements not found');
        return;
    }

    // Toggle menu
    hamburgerToggle.addEventListener('click', function(e) {
        e.stopPropagation();
        mobileMenuOverlay.classList.toggle('active');
        console.log('📱 Mobile menu toggled');
    });

    // Close menu when clicking close button
    closeMenu.addEventListener('click', function(e) {
        e.stopPropagation();
        mobileMenuOverlay.classList.remove('active');
        console.log('📱 Mobile menu closed');
    });

    // Close menu when clicking outside
    document.addEventListener('click', function(e) {
        if (mobileMenuOverlay.classList.contains('active') &&
            !mobileMenuOverlay.contains(e.target) &&
            !hamburgerToggle.contains(e.target)) {
            mobileMenuOverlay.classList.remove('active');
            console.log('📱 Mobile menu closed via outside click');
        }
    });

    // Close menu when clicking logout
    const logoutBtn = mobileMenuOverlay.querySelector('.btn-logout-mobile');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', function() {
            mobileMenuOverlay.classList.remove('active');
        });
    }

    // Close menu on window resize if going to desktop
    window.addEventListener('resize', function() {
        if (window.innerWidth >= 768) {
            mobileMenuOverlay.classList.remove('active');
        }
    });

    console.log('✅ Mobile hamburger menu initialized');
})();