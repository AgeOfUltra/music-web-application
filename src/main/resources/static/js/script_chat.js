// ==================== GLOBAL VARIABLES ====================
let stompClient = null;
let isLoadingSongs = false;
let currentRoomName = null;
let currentUsername = null;
let currentUserColor = null;
let currentUserDarkerColor = null;
let participantRefreshInterval = null;
let isOrganizer = false;
let ignoreLocalEvents = false;
let jwtToken = null;
let audioLoadTimeout = null;
let currentSongData = null;
const userColors = {};
const colors = ['#1a1a1a', '#2d2d2d', '#3d3d3d', '#505050', '#636363', '#767676'];
let currentPage = 0;
let totalPages = 1;
let currentParticipants = [];
let lastUserLeft = null;
let boundAudioRole = null;
let lastKnownOrganizer = null;
let allSongsOnPage = [];
let searchSongsList = [];

// ==================== SHARED ROOM FAVORITES ====================
let roomFavorites = [];
let isPlayingFavorites = false;
let currentFavoriteIndex = 0;

// ==================== PLAYLIST MANAGEMENT ====================
let currentPlaylist = [];
let currentPlaylistIndex = 0;
let playlistMode = null;
let syncRequestPending = false;
let pendingSyncRequests = 0; // Track concurrent requests
// ==================== TYPING INDICATOR ====================
let typing = false;
let typingTimeout;

// ==================== NEW: SYNC STATE TRACKING ====================
let isSyncing = false;
let syncTimeout = null;
let hasReceivedInitialSync = false;

let lastPlaybackAction = null;
let lastPlaybackTimestamp = 0;

let replyingToMessage = null;
let logoutInProgress = false;

let playDebounceTimer = null;
let pauseDebounceTimer = null;
let resumeDebounceTimer = null;
// ================== Constants ===============================
const DEBOUNCE_DELAY = 300;
const METADATA_TIMEOUT = 80000;
const DUPLICATE_EVENT_THRESHOLD = 500;

let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 5;
const RECONNECT_RESET_TIME = 60000; // Reset counter after 1 minute of success
let lastSuccessfulConnection = null;


let sessionExpired = false;
//  =========================== Debug

const DEBUG = false; // Set to false to disable all console.logs

if (!DEBUG) {
    console.log = function() {};
}

// ==================== GLOBAL ERROR HANDLER ====================
const toastRateLimiter = {
    lastToast: {}, rateLimits: {
        'favorites-update': 2000, 'playback-change': 1000, 'participant-change': 3000, 'default': 2000
    },

    canShow(key, customCooldown = null) {
        const now = Date.now();
        const cooldown = customCooldown || this.rateLimits[key] || this.rateLimits['default'];

        if (this.lastToast[key] && (now - this.lastToast[key]) < cooldown) {
            return false;
        }

        this.lastToast[key] = now;
        return true;
    },

    reset(key) {
        if (key) {
            delete this.lastToast[key];
        } else {
            this.lastToast = {};
        }
    }
};

window.addEventListener('error', (event) => {
    console.error('❌ Global error:', event.error);
    if (typeof ToastNotification !== 'undefined') {
        ToastNotification.error('An unexpected error occurred');
    }
    // Prevent default browser error display
    event.preventDefault();
});

window.addEventListener('unhandledrejection', (event) => {
    console.error('❌ Unhandled promise rejection:', event.reason);
    if (typeof ToastNotification !== 'undefined') {
        ToastNotification.error('Operation failed unexpectedly');
    }
    event.preventDefault();
});

// ==================== FAVORITES MANAGEMENT ====================
function toggleFavorite(song, heartIcon) {
    const songId = song.fileName;
    const existingFavorite = roomFavorites.find(f => f.fileName === songId);

    if (existingFavorite) {
        // Remove from favorites
        const updatedFavorites = roomFavorites.filter(f => f.fileName !== songId);
        broadcastFavorites(updatedFavorites, 'REMOVE', song, currentUsername);
        ToastNotification.info(`Removed "${song.songName}" from room favorites`);
    } else {
        // Add to favorites
        const favoriteItem = {
            ...song, requestedBy: currentUsername, requestedAt: Date.now()
        };
        const updatedFavorites = [...roomFavorites, favoriteItem];
        broadcastFavorites(updatedFavorites, 'ADD', favoriteItem, currentUsername);
        ToastNotification.success(`Added "${song.songName}" to room favorites`);
    }
}

function sendTypingEvent(isTyping) {
    if (!stompClient || !stompClient.connected) return;

    const typingMsg = {
        sender: currentUsername, typing: isTyping
    };

    stompClient.send(`/app/music/chat/${currentRoomName}/typing`, {}, JSON.stringify(typingMsg));
}

document.getElementById("messageInput").addEventListener("input", () => {
    if (!typing) {
        typing = true;
        sendTypingEvent(true);
    }

    clearTimeout(typingTimeout);
    typingTimeout = setTimeout(() => {
        typing = false;
        sendTypingEvent(false);
    }, 1200);
});

function broadcastFavorites(favorites, action, song, username) {
    if (!stompClient || !stompClient.connected) {
        console.error('Cannot broadcast favorites - WebSocket not connected');
        ToastNotification.error('WebSocket not connected');
        return;
    }

    // console.log('📤 Broadcasting favorites:', action, song?.songName);

    const message = {
        action: action, favorites: favorites, song: song, username: username, timestamp: Date.now()
    };

    try {
        stompClient.send(`/app/music/chat/${currentRoomName}/favorites`, {}, JSON.stringify(message));

        // console.log('✅ Favorites broadcast successful');

        // Update local state immediately
        roomFavorites = favorites;
        updateFavoritesDisplay();
        updateAllHeartIcons();

    } catch (error) {
        console.error('❌ Error broadcasting favorites:', error);
        ToastNotification.error('Failed to update favorites');
    }
}

function handleFavoritesUpdate(message) {
    roomFavorites = message.favorites || [];
    updateFavoritesDisplay();
    updateAllHeartIcons();

    if (message.username !== currentUsername) {
        // ✅ FIX: Rate limit favorite notifications
        const toastKey = `favorites-${message.action}`;

        if (toastRateLimiter.canShow(toastKey)) {
            if (message.action === 'ADD' && message.song) {
                ToastNotification.info(`${message.username} added "${message.song.songName}" to favorites`, 3000);
            } else if (message.action === 'REMOVE' && message.song) {
                ToastNotification.info(`${message.username} removed "${message.song.songName}" from favorites`, 3000);
            } else if (message.action === 'CLEAR') {
                ToastNotification.warning(`${message.username} cleared all favorites`, 3000);
            }
        }
    }
}


function updateFavoritesDisplay() {
    const favoritesList = document.getElementById('favoritesList');
    const favoritesCount = document.getElementById('favoritesCount');

    if (!favoritesList) return;

    favoritesCount.textContent = roomFavorites.length;

    if (roomFavorites.length === 0) {
        favoritesList.innerHTML = `
            <div style="display: flex; flex-direction: column; justify-content: center; align-items: center; min-height: 200px; color: #a0a0a0; text-align: center; padding: 20px;">
                <div style="font-size: 48px; margin-bottom: 15px; opacity: 0.5;">♥</div>
                <p>No song requests yet</p>
                <p style="font-size: 12px; opacity: 0.7;">Anyone can add songs by clicking the ♥ icon</p>
            </div>
        `;
        return;
    }

    favoritesList.innerHTML = '';

    roomFavorites.forEach((favorite, index) => {
        const songItem = document.createElement('div');
        songItem.className = 'favorite-song-item';

        const isMyRequest = favorite.requestedBy === currentUsername;

        songItem.innerHTML = `
            <div class="favorite-song-number">${index + 1}</div>
            <div class="favorite-song-info">
                <div class="favorite-song-title">${favorite.songName}</div>
                <div class="favorite-song-details">
                    ${favorite.hero || favorite.singer || 'Unknown'} • ${favorite.language || 'Unknown'}
                    <span style="color: ${isMyRequest ? '#10b981' : '#ec4899'}; font-weight: 500; margin-left: 8px;">
                        • by ${favorite.requestedBy}
                    </span>
                </div>
            </div>
            <button class="favorite-remove-btn" onclick="removeFavorite('${favorite.fileName}')" title="Remove from favorites">
                <i class="bi bi-x-lg"></i>
            </button>
        `;

        songItem.onclick = (e) => {
            if (!e.target.closest('.favorite-remove-btn')) {
                if (isOrganizer) {
                    playSong(favorite);
                } else {
                    ToastNotification.warning('Only the organizer can play songs');
                }
            }
        };

        favoritesList.appendChild(songItem);
    });
}

function updateAllHeartIcons() {
    // console.log('💖 Updating heart icons, favorites count:', roomFavorites.length);

    // Update heart icons in main song list
    const songItems = document.querySelectorAll('#songList .song-item');
    songItems.forEach(item => {
        const fileName = item.dataset.filename;
        const heartIcon = item.querySelector('.favorite-heart');
        if (heartIcon && fileName) {
            const isFavorite = roomFavorites.some(f => f.fileName === fileName);
            heartIcon.classList.remove('bi-heart', 'bi-heart-fill');
            heartIcon.classList.add(isFavorite ? 'bi-heart-fill' : 'bi-heart');
            // console.log(`  - ${fileName}: ${isFavorite ? 'favorited' : 'not favorited'}`);
        }
    });

    // Update heart icons in search results
    const searchItems = document.querySelectorAll('#searchResults .song-item');
    searchItems.forEach(item => {
        const fileName = item.dataset.filename;
        const heartIcon = item.querySelector('.favorite-heart');
        if (heartIcon && fileName) {
            const isFavorite = roomFavorites.some(f => f.fileName === fileName);
            heartIcon.classList.remove('bi-heart', 'bi-heart-fill');
            heartIcon.classList.add(isFavorite ? 'bi-heart-fill' : 'bi-heart');
        }
    });
}

function removeFavorite(fileName) {
    const favorite = roomFavorites.find(f => f.fileName === fileName);
    if (!favorite) return;

    // ⭐ NEW: Permission check
    const canRemove = isOrganizer || favorite.requestedBy === currentUsername;

    if (!canRemove) {
        ToastNotification.warning('You can only remove songs you added to favorites');
        return;
    }

    const updatedFavorites = roomFavorites.filter(f => f.fileName !== fileName);
    broadcastFavorites(updatedFavorites, 'REMOVE', favorite, currentUsername);
    ToastNotification.success(`Removed "${favorite.songName}" from favorites`);
}

function playFavoritesPlaylist() {
    if (!isOrganizer) {
        ToastNotification.warning('Only the organizer can play songs');
        return;
    }

    if (roomFavorites.length === 0) {
        ToastNotification.warning('No songs in favorites playlist');
        return;
    }

    isPlayingFavorites = true;
    currentFavoriteIndex = 0;
    playlistMode = 'favorites';
    currentPlaylist = [...roomFavorites];
    currentPlaylistIndex = 0;

    ToastNotification.success(`▶️ Starting favorites playlist (${roomFavorites.length} songs)`);
    playSong(roomFavorites[0]);
}


function openFavoritesDrawer() {
    document.getElementById('favoritesDrawer').classList.add('open');
}

function closeFavoritesDrawer() {
    document.getElementById('favoritesDrawer').classList.remove('open');
}

function clearAllFavorites() {
    if (!isOrganizer) {
        ToastNotification.warning('Only the organizer can clear the playlist');
        return;
    }
    if (roomFavorites.length === 0) return;

    if (confirm(`Are you sure you want to clear all ${roomFavorites.length} favorite songs? This will affect all participants.`)) {
        broadcastFavorites([], 'CLEAR', null, currentUsername);
        ToastNotification.success('Cleared all room favorites');
    }
}

// ==================== AUDIO ROLE MANAGEMENT ====================
function syncAudioWrapperClass() {
    const wrapper = document.getElementById('audioPlayerWrapper');
    if (!wrapper) return;

    wrapper.classList.remove('organizer-audio', 'readonly-audio');
    wrapper.classList.add(isOrganizer ? 'organizer-audio' : 'readonly-audio');
}

const onOrganizerPause = (e) => {
    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) return;
    if (!isOrganizer) return;

    if (!ignoreLocalEvents && e.isTrusted && stompClient?.connected && audioPlayer.src) {
        // ✅ FIX: Use dedicated pause timer
        if (pauseDebounceTimer) {
            clearTimeout(pauseDebounceTimer);
        }

        const currentTime = Math.floor(audioPlayer.currentTime * 1000);

        if (lastPlaybackAction === 'PAUSE' && Math.abs(currentTime - lastPlaybackTimestamp) < DUPLICATE_EVENT_THRESHOLD) {
            return;
        }

        pauseDebounceTimer = setTimeout(() => {
            lastPlaybackAction = 'PAUSE';
            lastPlaybackTimestamp = currentTime;

            const playbackMessage = {
                action: 'PAUSE', timestamp: currentTime, controller: currentUsername
            };

            stompClient.send(`/app/music/chat/${currentRoomName}/playback`, {}, JSON.stringify(playbackMessage));
            pauseDebounceTimer = null; // ✅ Clear after sending
        }, DEBOUNCE_DELAY);
    }
};

const onOrganizerPlay = (e) => {
    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) return;
    if (!isOrganizer) return;

    if (!ignoreLocalEvents && e.isTrusted && stompClient?.connected && audioPlayer.src && currentSongData) {
        const currentTime = Math.floor(audioPlayer.currentTime * 1000);
        const action = currentTime > 1000 ? 'RESUME' : 'PLAY';

        // ✅ FIX: Use appropriate timer based on action
        if (action === 'PLAY' && playDebounceTimer) {
            clearTimeout(playDebounceTimer);
        } else if (action === 'RESUME' && resumeDebounceTimer) {
            clearTimeout(resumeDebounceTimer);
        }

        if (action === 'RESUME' && lastPlaybackAction === 'RESUME' && Math.abs(currentTime - lastPlaybackTimestamp) < DUPLICATE_EVENT_THRESHOLD) {
            return;
        }

        const timer = setTimeout(() => {
            lastPlaybackAction = action;
            lastPlaybackTimestamp = currentTime;

            const playbackMessage = {
                action: action,
                timestamp: currentTime,
                controller: currentUsername,
                songFileName: currentSongData.songFileName,
                songName: currentSongData.songName,
                hero: currentSongData.hero,
                heroine: currentSongData.heroine,
                language: currentSongData.language
            };

            stompClient.send(`/app/music/chat/${currentRoomName}/playback`, {}, JSON.stringify(playbackMessage));

            // ✅ Clear the timer
            if (action === 'PLAY') playDebounceTimer = null; else resumeDebounceTimer = null;
        }, DEBOUNCE_DELAY);

        // ✅ Store the timer
        if (action === 'PLAY') playDebounceTimer = timer; else resumeDebounceTimer = timer;
    }
};

const onParticipantPlay = (e) => {
    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) return;
    if (isOrganizer) return;

    if (!ignoreLocalEvents && e.isTrusted && audioPlayer.paused) {
        e.preventDefault();
        try {
            audioPlayer.pause();
        } catch (_) {
        }
        ToastNotification.warning('Only the organizer can control playback');
    }
};
const onParticipantPause = (e) => {
    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) return;
    if (isOrganizer) return;
    if (!ignoreLocalEvents && e.isTrusted) {
        e.preventDefault();
        audioPlayer.play().catch(() => {
        }); // Silent for participants
    }
};

function bindAudioHandlersForRole(role) {
    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) return;

    audioPlayer.removeEventListener('pause', onOrganizerPause);
    audioPlayer.removeEventListener('play', onOrganizerPlay);
    audioPlayer.removeEventListener('play', onParticipantPlay, true);
    audioPlayer.removeEventListener('pause', onParticipantPause, true);
    audioPlayer.removeEventListener('ended', onSongEnded);

    if (role === 'organizer') {
        audioPlayer.addEventListener('pause', onOrganizerPause);
        audioPlayer.addEventListener('play', onOrganizerPlay);
        audioPlayer.addEventListener('ended', onSongEnded);
    } else {
        audioPlayer.addEventListener('play', onParticipantPlay, true);
        audioPlayer.addEventListener('pause', onParticipantPause, true);
    }

    boundAudioRole = role;
}

function onSongEnded() {
    if (!isOrganizer) return;

    const audioPlayer = document.getElementById('audioPlayer');

    // ✅ NEW: Check if song actually ended naturally (not due to error)
    if (audioPlayer.error || audioPlayer.networkState === 3) {
        console.warn('⚠️ Song ended due to error/network issue, not auto-playing next');
        return;
    }

    // ✅ NEW: Check if song duration is reasonable (>10 seconds played)
    if (audioPlayer.currentTime < 10) {
        console.warn('⚠️ Song ended too quickly (likely an error), not auto-playing next');
        return;
    }

    const current = currentSongData?.songFileName;
    if (!current) return;

    console.log('🎵 Song ended:', currentSongData?.songName);
    console.log('📊 Current mode:', playlistMode);

    let nextSong = null;
    let nextIndex = -1;

    // ==================== MODE 1: FAVORITES (ISOLATED) ====================
    if (playlistMode === 'favorites') {
        const favIndex = roomFavorites.findIndex(s => s.fileName === current);

        if (favIndex !== -1 && favIndex < roomFavorites.length - 1) {
            // Continue in favorites
            nextIndex = favIndex + 1;
            nextSong = roomFavorites[nextIndex];
            currentFavoriteIndex = nextIndex;
            currentPlaylistIndex = nextIndex;
            console.log('✅ Next from favorites:', nextSong.songName);
        } else {
            // End of favorites - STOP
            console.log('🏁 End of favorites playlist');
            ToastNotification.info('🎵 End of favorites playlist');
            playlistMode = null;
            return;
        }
    }

    // ==================== MODE 2: SEARCH RESULTS (ISOLATED) ====================
    else if (playlistMode === 'search') {
        const searchIndex = searchSongsList.findIndex(s => s.fileName === current);

        if (searchIndex !== -1 && searchIndex < searchSongsList.length - 1) {
            // Continue in search results
            nextIndex = searchIndex + 1;
            nextSong = searchSongsList[nextIndex];
            currentPlaylistIndex = nextIndex;
            console.log('✅ Next from search:', nextSong.songName);
        } else {
            // End of search results - STOP
            console.log('🏁 End of search results');
            ToastNotification.info('🎵 End of search results');
            playlistMode = null;
            return;
        }
    }

    // ==================== MODE 3: GLOBAL PLAYLIST (WITH PAGINATION) ====================
    else if (playlistMode === 'all') {
        const pageIndex = allSongsOnPage.findIndex(s => s.fileName === current);

        if (pageIndex !== -1 && pageIndex < allSongsOnPage.length - 1) {
            // Next song on same page
            nextIndex = pageIndex + 1;
            nextSong = allSongsOnPage[nextIndex];
            currentPlaylistIndex = nextIndex;
            console.log('✅ Next song on same page:', nextSong.songName);
        } else if (currentPage < totalPages - 1) {
            // Load next page
            console.log('📀 Loading next page...');
            loadNextPageAndPlay();
            return;
        } else {
            // End of all pages
            console.log('🏁 End of global playlist');
            ToastNotification.info('🎵 End of playlist');
            playlistMode = null;
            return;
        }
    }

    // ==================== NO MODE - SHOULDN'T HAPPEN ====================
    else {
        console.log('⚠️ No active playlist mode');
        ToastNotification.info('🎵 Playback ended');
        return;
    }

    // ==================== PLAY NEXT SONG ====================
    if (nextSong) {
        console.log('▶️ Auto-playing next:', nextSong.songName);
        setTimeout(() => playSong(nextSong), 700);
    }
}

function onRoleChange() {
    updatePermissionNotice();
    syncAudioWrapperClass();
    updateAudioControls();
    updatePlaybackButtons();

    // ✅ FIX: Clear ALL debounce timers
    if (playDebounceTimer) {
        clearTimeout(playDebounceTimer);
        playDebounceTimer = null;
    }
    if (pauseDebounceTimer) {
        clearTimeout(pauseDebounceTimer);
        pauseDebounceTimer = null;
    }
    if (resumeDebounceTimer) {
        clearTimeout(resumeDebounceTimer);
        resumeDebounceTimer = null;
    }

    lastPlaybackAction = null;
    lastPlaybackTimestamp = 0;

    const desired = isOrganizer ? 'organizer' : 'participant';
    if (boundAudioRole !== desired) {
        bindAudioHandlersForRole(desired);
    }
}


function resetAudioPlayer() {
    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) return;

    // console.log('🔄 Resetting audio player to clean state');

    try {
        // Stop playback
        audioPlayer.pause();

        // Clear source completely
        audioPlayer.src = "";
        audioPlayer.removeAttribute("src");
        audioPlayer.load(); // Force browser to clear cache

        // Reset currentTime
        audioPlayer.currentTime = 0;

    } catch (e) {
        console.warn('⚠️ Audio reset error:', e);
    }

    // Clear UI
    document.getElementById('currentSongTitle').textContent = "No song playing";
    document.getElementById('currentSongDetails').textContent = "Select a song to play";

    // Clear state variables
    currentSongData = null;
    ignoreLocalEvents = false;
    isPlayingFavorites = false;
    currentFavoriteIndex = 0;
    playlistMode = null;
    currentPlaylist = [];
    currentPlaylistIndex = 0;
}

// ==================== PAGE INITIALIZATION ====================
window.onload = function () {
    if (isReload()) {
        // console.log('🔄 Reload detected on page load - redirecting to dashboard');
        window.location.href = '/app/music/public/logout';
        return;
    }

    initializePage();
};

function initializePage() {
    currentRoomName = PAGE_DATA.roomName;
    currentUsername = PAGE_DATA.username;
    jwtToken = PAGE_DATA.jwtToken;

// Initialize current user's colors
    currentUserColor = getColorForUser(currentUsername);
    currentUserDarkerColor = getDarkerShade(currentUserColor);
    isOrganizer = PAGE_DATA.isOrganizer;

    if (!jwtToken || !currentUsername || !currentRoomName) {
        console.error('Missing required data:', {jwtToken, currentUsername, currentRoomName});
        window.location.href = '/app/music/public/login';
        return;
    }

    updatePermissionNotice();
    setupAudioPlayerListeners();
    connectWebSocket(jwtToken);
    loadSongsForPage(0);
    initializeToastNotifications();

}

// ==================== NETWORK STATUS MONITORING ====================
let wasOffline = false;

window.addEventListener('online', () => {
    if (wasOffline) {
        ToastNotification.success('🌐 Connection restored. Reconnecting...');

        // Reconnect WebSocket
        if (stompClient && !stompClient.connected) {
            setTimeout(() => connectWebSocket(jwtToken), 1000);
        }

        // Refresh participants
        setTimeout(() => refreshParticipants(), 5000);

        wasOffline = false;
    }
});

window.addEventListener('offline', () => {
    wasOffline = true;
    ToastNotification.warning('🌐 No internet connection', 0); // Persistent toast
});

// ======================== HANDLE EXPIRED SESSION ============================
function handleSessionExpired(reason = 'Session expired') {
    if (logoutInProgress) return;
    logoutInProgress = true;

    sessionExpired = true;

    console.warn('⏱ Session expired:', reason);
    ToastNotification.error('Session expired. Logging out...', 2000);

    cleanupIntervals();

    // ✅ CRITICAL: Exit room with beacon BEFORE redirect
    // if (currentRoomName && currentUsername) {
    //     sendExitBeacon(true); // fullLogout = true
    // }

    // Disconnect WebSocket
    try {
        if (stompClient && stompClient.connected) {
            stompClient.disconnect(() => {
                console.log('🔌 WebSocket disconnected due to session expiry');
            });
        }
    } catch (e) {
        console.error('Error disconnecting WebSocket', e);
    }

    // Clear client state
    jwtToken = null;
    currentUsername = null;
    currentRoomName = null;
    // ✅ Mark clean exit to prevent beacon
    sessionStorage.setItem('cleanExit', 'true');

    // ✅ Redirect after beacon has time to send (beacons are async but queued)
    setTimeout(() => {
        window.location.href = '/app/music/public/login?expired=true';
    }, 500); // Reduced from 1500ms - beacons are fire-and-forget
}


// ==================== FETCH WITH TIMEOUT ====================
async function fetchWithTimeout(url, options = {}, timeout = 8000) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);

    try {
        const response = await fetch(url, {
            ...options, signal: controller.signal, headers: {
                ...options.headers, 'X-Requested-With': 'XMLHttpRequest', 'Authorization': `Bearer ${jwtToken}`
            }
        });

        clearTimeout(timeoutId);

        // 🔥 HARD AUTH CHECK
        if (response.status === 401 || response.status === 403) {
            handleSessionExpired(`HTTP ${response.status}`);
            throw new Error('Session expired');
        }

        return response;

    } catch (error) {
        clearTimeout(timeoutId);

        if (error.name === 'AbortError') {
            throw new Error('Request timeout');
        }

        throw error;
    }
}


// ==================== PAGINATION FUNCTIONS ====================
async function loadSongsForPage(pageNumber) {
    if (isLoadingSongs) return;

    isLoadingSongs = true;
    showLoadingState();

    try {
        const response = await fetch(`/app/music/audio/fetchAllSongs?page=${pageNumber}&size=10`, {
            headers: {
                'Authorization': `Bearer ${jwtToken}`
            }
        });

        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

        const data = await response.json();
        const pageData = data.page || data;

        currentPage = pageData.number ?? pageNumber;
        totalPages = pageData.totalPages ?? 1;

        displaySongs(data.content || []);
        updatePaginationUI();

    } catch (error) {
        console.error('Error loading songs:', error);
        ToastNotification.error(error.message === 'Request timeout' ? 'Request timed out. Please try again.' : 'Failed to load songs');
        showEmptyState();
    } finally {
        isLoadingSongs = false;
    }
}

function nextPage() {
    if (currentPage < totalPages - 1) {
        loadSongsForPage(currentPage + 1);
    }
}

function previousPage() {
    if (currentPage > 0) {
        loadSongsForPage(currentPage - 1);
    }
}


function updatePaginationUI() {
    const currentPageElement = document.querySelector('.current-page');
    const totalPagesElement = document.querySelector('.total-pages');
    const currentPageNum = document.getElementById('currentPageNum');
    const totalPagesNum = document.getElementById('totalPagesNum');
    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');

    if (!currentPageElement || !totalPagesElement || !currentPageNum || !totalPagesNum || !prevBtn || !nextBtn) {
        console.warn('⚠️ Pagination elements not found');
        return;
    }

    const displayPage = currentPage + 1;
    currentPageElement.textContent = displayPage;
    totalPagesElement.textContent = totalPages;
    currentPageNum.textContent = displayPage;
    totalPagesNum.textContent = totalPages;
    prevBtn.disabled = currentPage === 0;
    nextBtn.disabled = currentPage >= totalPages - 1;
}

function showLoadingState() {
    document.getElementById('songList').innerHTML = `
        <div style="display: flex; justify-content: center; align-items: center; min-height: 300px; color: #a0a0a0;">
            <div style="text-align: center;">
                <div style="border: 3px solid #404040; border-top: 3px solid #7c3aed; border-radius: 50%; width: 30px; height: 30px; animation: spin 0.8s linear infinite; margin: 0 auto 10px;"></div>
                Loading songs...
            </div>
        </div>
    `;
}

function showEmptyState() {
    document.getElementById('songList').innerHTML = `
        <div style="display: flex; flex-direction: column; justify-content: center; align-items: center; min-height: 300px; color: #a0a0a0; text-align: center;">
            <div style="font-size: 48px; margin-bottom: 15px; opacity: 0.5;">🎵</div>
            <p>No songs found on this page</p>
        </div>
    `;
}

// ==================== TOAST NOTIFICATION SYSTEM ====================
// ✅ Toast Rate Limiter

const ToastNotification = {
    show: function (message, type = 'info', duration = 3000) {
        const toastContainer = document.getElementById('toastContainer');
        if (!toastContainer) {
            console.error('❌ [TOAST] Toast container not found!');
            return;
        }

        const toastDiv = document.createElement('div');
        toastDiv.className = `toast ${type}`;

        toastDiv.style.cssText = `
            min-width: 300px;
            max-width: 500px;
            margin-bottom: 10px;
            padding: 16px;
            border-radius: 8px;
            color: white;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
            display: flex;
            align-items: center;
            gap: 12px;
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.1);
            font-family: "Inter", sans-serif;
            pointer-events: auto;
            z-index: 9999;
            position: relative;
        `;

        if (type === 'success') toastDiv.style.backgroundColor = '#4caf50'; else if (type === 'error') toastDiv.style.backgroundColor = '#f44336'; else if (type === 'info') toastDiv.style.backgroundColor = '#2196F3'; else if (type === 'warning') toastDiv.style.backgroundColor = '#ff9800'; else toastDiv.style.backgroundColor = '#323232';

        const icons = {
            success: '✓', error: '✕', info: 'ℹ', warning: '⚠'
        };

        toastDiv.innerHTML = `
            <span class="toast-icon" style="font-size: 18px; flex-shrink: 0; font-weight: bold;">${icons[type] || icons.info}</span>
            <span class="toast-message" style="flex: 1; line-height: 1.4; word-break: break-word;">${message}</span>
            <button class="toast-close" style="background: none; border: none; color: white; cursor: pointer; font-size: 20px; padding: 0; flex-shrink: 0; width: 24px; height: 24px;">×</button>
        `;

        toastContainer.appendChild(toastDiv);

        toastDiv.querySelector('.toast-close').addEventListener('click', () => {
            this.removeToast(toastDiv);
        });

        if (duration > 0) {
            setTimeout(() => {
                this.removeToast(toastDiv);
            }, duration);
        }
    },

    removeToast: function (toastDiv) {
        toastDiv.classList.add('removing');
        setTimeout(() => {
            toastDiv.remove();
        }, DEBOUNCE_DELAY);
    },

    success: function (message, duration = 3000) {
        this.show(message, 'success', duration);
    },

    error: function (message, duration = 3000) {
        this.show(message, 'error', duration);
    },

    info: function (message, duration = 3000) {
        this.show(message, 'info', duration);
    },

    warning: function (message, duration = 3000) {
        this.show(message, 'warning', duration);
    }
};

function initializeToastNotifications() {
    const successCreate = document.getElementById('successMessageCreate')?.textContent?.trim();
    const successJoin = document.getElementById('successMessageJoin')?.textContent?.trim();
    if (successCreate) {
        ToastNotification.success(successCreate);
    }
    if (successJoin) {
        ToastNotification.success(successJoin);
    }
}

window.addEventListener('DOMContentLoaded', () => {
    const toastContainer = document.getElementById('toastContainer');
    if (!toastContainer) {
        console.error('❌ [INIT] Toast container NOT found on page load!');
    }
});

// ==================== UPDATE CURRENT SONG DISPLAY ====================
function updateCurrentSongDisplay(songName, hero, language, movie, singer) {
    const titleElement = document.getElementById('currentSongTitle');
    const detailsElement = document.getElementById('currentSongDetails');

    if (!titleElement || !detailsElement) {
        console.warn('⚠️ Song display elements not found');
        return;
    }

    titleElement.textContent = songName;
    const details = [hero || movie, singer || movie, language]
        .filter(Boolean)
        .join(' • ');
    detailsElement.textContent = details || 'Unknown details';
}


// ==================== AUDIO PLAYER CONTROL ====================
function setupAudioPlayerListeners() {
    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) {
        console.error('❌ Audio player not found!');
        return;
    }
    syncAudioWrapperClass();
    updateAudioControls();
    updatePlaybackButtons();
    bindAudioHandlersForRole(isOrganizer ? 'organizer' : 'participant');

    // ✅ NEW: Add error handler to catch loading failures
    audioPlayer.addEventListener('error', (e) => {
        console.error('❌ Audio error:', e);

        // Clear timeout if it exists
        if (audioLoadTimeout) {
            clearTimeout(audioLoadTimeout);
            audioLoadTimeout = null;
        }

        ignoreLocalEvents = false;

        const errorMsg = audioPlayer.error ? `Error code: ${audioPlayer.error.code}` : 'Unknown error';

        console.error('Audio error details:', errorMsg);

        ToastNotification.error('❌ Failed to load audio. Please try a different song or check your connection.', 6000);

        // Reset display
        currentSongData = null;
        document.getElementById('currentSongTitle').textContent = "Playback failed";
        document.getElementById('currentSongDetails').textContent = "Try playing the song again";
    });

    // ✅ NEW: Add stalled handler for slow loading
    audioPlayer.addEventListener('stalled', () => {
        console.warn('⚠️ Audio loading stalled');
        ToastNotification.warning('Song loading is slow. Please wait...', 4000);
    });
}

// ==================== PLAYBACK HANDLING ====================
function handlePlaybackCommand(playbackMsg) {
    // console.log('🔔 handlePlaybackCommand triggered:', {
    //     action: playbackMsg.action,
    //     songName: playbackMsg.songName,
    //     ignoreLocalEvents: ignoreLocalEvents,
    //     audioPlayerExists: !!document.getElementById('audioPlayer')
    // });
    const audioPlayer = document.getElementById('audioPlayer');

    if (!audioPlayer) {
        console.error('❌ Audio player not found in handlePlaybackCommand!');
        return;
    }

    // ⭐ FIX 5: Clear the playback timeout since we got a response
    if (window.currentPlaybackTimeout) {
        clearTimeout(window.currentPlaybackTimeout);
        window.currentPlaybackTimeout = null;
    }

    ignoreLocalEvents = true;

    if (audioLoadTimeout) {
        clearTimeout(audioLoadTimeout);
    }

    if (playbackMsg.action === 'ERROR') {
        console.error('❌ Playback error:', playbackMsg.content);
        ToastNotification.error('Error: ' + playbackMsg.content);
        ignoreLocalEvents = false;
        return;
    }

    const controller = playbackMsg.controller;

    if (controller === lastUserLeft) {
        console.warn("⏭️ Ignoring playback from user who just left:", controller);
        ignoreLocalEvents = false;
        return;
    }

    if (isOrganizer && controller !== currentUsername) {
        console.warn("⏭️ Ignoring playback event because I am the organizer now:", controller);
        ignoreLocalEvents = false;
        return;
    }

    const stillInRoom = currentParticipants.some(p => p.userName === controller);

    if (!stillInRoom) {
        console.warn("⏭️ Ignoring stale playback event from user who left:", controller);
        ignoreLocalEvents = false;
        return;
    }

    switch (playbackMsg.action) {
        case 'PLAY':
            handlePlayCommand(audioPlayer, playbackMsg);
            break;

        case 'PAUSE':
            handlePauseCommand(audioPlayer, playbackMsg);
            break;

        case 'RESUME':
            handleResumeCommand(audioPlayer, playbackMsg);
            break;

        default:
            console.warn('⚠️ Unknown playback action:', playbackMsg.action);
            ignoreLocalEvents = false;
    }

    setTimeout(() => {
        if (ignoreLocalEvents) {
            console.warn('⚠️ Clearing ignoreLocalEvents safety-net after 10s');
            ignoreLocalEvents = false;
        }
    }, 10000);
}


function handlePlayCommand(audioPlayer, playbackMsg) {
    // console.log('🎵 handlePlayCommand called:', {
    //     songName: playbackMsg.songName,
    //     fileName: playbackMsg.songFileName,
    //     currentSrc: audioPlayer.src,
    //     ignoreLocalEvents: ignoreLocalEvents
    // });

    const newSrc = `/app/music/audio/public/streamSong/${playbackMsg.songFileName}?t=${Date.now()}`;

    currentSongData = {
        songFileName: playbackMsg.songFileName,
        songName: playbackMsg.songName,
        hero: playbackMsg.hero,
        heroine: playbackMsg.heroine,
        language: playbackMsg.language,
        movie: playbackMsg.movie
    };

    updateCurrentSongDisplay(playbackMsg.songName, playbackMsg.hero, playbackMsg.language, playbackMsg.movie, playbackMsg.singer);

    const isOwnAction = playbackMsg.controller === currentUsername;

    // ⭐ Show appropriate toast
    if (!isOwnAction && toastRateLimiter.canShow('playback-info')) {
        ToastNotification.info(`🎵 ${playbackMsg.controller} is playing: ${playbackMsg.songName}`, 2000);
    }

    // ⭐ FIX: Simplified source change detection
    const currentSrc = audioPlayer.src || '';
    const sourceChanged = !currentSrc.includes(playbackMsg.songFileName);

    // console.log('🔍 Source check:', {
    //     currentSrc: currentSrc,
    //     newSrc: newSrc,
    //     sourceChanged: sourceChanged
    // });

    // ⭐ ALWAYS set the source to ensure it loads
    audioPlayer.src = newSrc;
    // console.log('✅ Audio source set to:', newSrc);

    const startTime = playbackMsg.timestamp ? playbackMsg.timestamp / 1000 : 0;

    // ⭐ FIX: Always wait for metadata, even if source "didn't change"
    let metadataLoaded = false;

    const metadataHandler = () => {
        // console.log('📊 Metadata loaded successfully');
        metadataLoaded = true;
        audioPlayer.currentTime = startTime;

        const playPromise = audioPlayer.play();

        if (playPromise !== undefined) {
            playPromise
                .then(() => {
                    ignoreLocalEvents = false;
                    // console.log('✅ Playback started successfully');
                    if (isOwnAction) {
                        ToastNotification.success(`✅ Now Playing: ${playbackMsg.songName}`, 500);
                    }
                })
                .catch(err => {
                    console.error('❌ Play error:', err.message);
                    ignoreLocalEvents = false;
                    ToastNotification.error(`❌ Failed to play: ${playbackMsg.songName}`, 4000);
                });
        } else {
            ignoreLocalEvents = false;
        }

        audioPlayer.removeEventListener('loadedmetadata', metadataHandler);
        if (audioLoadTimeout) clearTimeout(audioLoadTimeout);
    };

    // ⭐ Check if metadata is already loaded
    if (audioPlayer.readyState >= 2) {
        // console.log('📊 Metadata already available, playing immediately');
        metadataHandler();
    } else {
        // console.log('⏳ Waiting for metadata to load...');
        audioPlayer.addEventListener('loadedmetadata', metadataHandler, {once: true});

        // ⭐ Timeout for large files
        // ⭐ Timeout for large files
        audioLoadTimeout = setTimeout(() => {
            if (!metadataLoaded) {
                console.warn('⚠️ Metadata loading timeout (60s)');
                audioPlayer.removeEventListener('loadedmetadata', metadataHandler);

                // ✅ FIX: Stop the audio completely instead of forcing play
                audioPlayer.pause();
                audioPlayer.src = '';
                audioPlayer.removeAttribute('src');

                ignoreLocalEvents = false;

                ToastNotification.error('❌ Song took too long to load. Please check your connection and try again.', 8000);

                // ✅ Optional: Reset current song display
                currentSongData = null;
                document.getElementById('currentSongTitle').textContent = "Loading failed";
                document.getElementById('currentSongDetails').textContent = "Please try playing the song again";
            }
        }, METADATA_TIMEOUT); // 60 seconds
    }
}

function handlePauseCommand(audioPlayer, playbackMsg) {
    audioPlayer.currentTime = playbackMsg.timestamp / 1000;
    audioPlayer.pause();

    const isOwnAction = playbackMsg.controller === currentUsername;
    if (isOwnAction) {
        ToastNotification.warning('⏸️ Music paused');
    } else {
        ToastNotification.warning(`⏸️ ${playbackMsg.controller} paused the music`);
    }

    setTimeout(() => {
        ignoreLocalEvents = false;
    }, 50);
}

function handleResumeCommand(audioPlayer, playbackMsg) {
    if (playbackMsg.timestamp !== undefined) {
        audioPlayer.currentTime = playbackMsg.timestamp / 1000;
    }

    const playPromise = audioPlayer.play();
    if (playPromise !== undefined) {
        playPromise
            .then(() => {
                ignoreLocalEvents = false;
            })
            .catch(err => {
                console.error('Resume error:', err.message);
                ignoreLocalEvents = false;
            });
    } else {
        ignoreLocalEvents = false;
    }

    const isOwnAction = playbackMsg.controller === currentUsername;
    if (isOwnAction) {
        ToastNotification.success('▶️ Music resumed');
    } else {
        ToastNotification.success(`▶️ ${playbackMsg.controller} resumed the music`);
    }
}

// ==================== FIXED: HANDLE PLAYBACK SYNC RESPONSE ====================
// ✅ Clock synchronization helpers
let serverTimeOffset = 0; // Difference between server and client time
let syncSamples = [];
const MAX_SYNC_SAMPLES = 5;

function updateServerTimeOffset(serverTime) {
    const clientTime = Date.now();
    const offset = serverTime - clientTime;

    syncSamples.push(offset);
    if (syncSamples.length > MAX_SYNC_SAMPLES) {
        syncSamples.shift();
    }

    // Use median to reduce impact of outliers
    const sortedSamples = [...syncSamples].sort((a, b) => a - b);
    serverTimeOffset = sortedSamples[Math.floor(sortedSamples.length / 2)];
}

function getAdjustedServerTime() {
    return Date.now() + serverTimeOffset;
}

function handlePlaybackSyncState(syncMsg) {
    syncRequestPending = false;
    hasReceivedInitialSync = true;

    if (syncTimeout) {
        clearTimeout(syncTimeout);
        syncTimeout = null;
    }

    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) {
        console.error('❌ [SYNC] Audio player not found');
        isSyncing = false;
        return;
    }

    if (!syncMsg.valid || !syncMsg.songFileName) {
        if (audioPlayer.src || !audioPlayer.paused) {
            resetAudioPlayer();
            ToastNotification.info('No active playback in room');
        }
        isSyncing = false;
        return;
    }

    if (isOrganizer) {
        if (!currentSongData && syncMsg.valid) {
            // Organizer has no playback but server does - stale state
        }
        isSyncing = false;
        return;
    }

    // ✅ FIX: Better sync calculation
    const serverTime = syncMsg.serverTime || Date.now();
    updateServerTimeOffset(serverTime);

    const messageAge = Date.now() - serverTime;

    if (messageAge > 30000) {
        console.warn('⚠️ [SYNC] Received stale sync message (age:', messageAge, 'ms) - ignoring');
        resetAudioPlayer();
        isSyncing = false;
        return;
    }

    // console.log('🎵 [SYNC] Syncing to active song:', syncMsg.songName);
    // console.log('📊 [SYNC] State details:', {
    //     isPlaying: syncMsg.isPlaying,
    //     isPaused: syncMsg.isPaused,
    //     timestamp: syncMsg.timestamp,
    //     serverTime: syncMsg.serverTime,
    //     messageAge: messageAge
    // });

    // console.log('🌐 [SYNC] Network delay:', networkDelay, 'ms');

    let adjustedTimestamp = syncMsg.timestamp || 0;

    if (syncMsg.isPlaying && !syncMsg.isPaused) {
        // Account for time since server generated the message
        const timeSinceMessage = Date.now() - serverTime;
        adjustedTimestamp += timeSinceMessage;

        console.log('⏱️ [SYNC] Adjusted timestamp:', {
            original: syncMsg.timestamp,
            messageAge: timeSinceMessage,
            adjusted: adjustedTimestamp,
            serverOffset: serverTimeOffset
        });
    }

    // Set ignore flag to prevent event triggers
    ignoreLocalEvents = true;

    // Update current song data
    currentSongData = {
        songFileName: syncMsg.songFileName,
        songName: syncMsg.songName,
        hero: syncMsg.hero,
        heroine: syncMsg.heroine,
        language: syncMsg.language,
        movie: syncMsg.movie,
        singer: syncMsg.singer
    };

    // console.log('📝 [SYNC] Updated current song data:', currentSongData);

    // Update UI
    updateCurrentSongDisplay(syncMsg.songName, syncMsg.hero, syncMsg.language, syncMsg.movie, syncMsg.singer);

    // Build audio source URL
    const audioSrc = `/app/music/audio/public/streamSong/${syncMsg.songFileName}?t=${Date.now()}`;
    // console.log('🔗 [SYNC] Loading audio from:', audioSrc);

    // Load and sync audio
    audioPlayer.src = audioSrc;

    const syncAudio = () => {
        const targetTime = adjustedTimestamp / 1000; // Convert to seconds

        console.log('🎯 [SYNC] Setting playback position to:', targetTime, 'seconds');

        audioPlayer.currentTime = targetTime;

        if (syncMsg.isPaused) {
            // console.log('⏸️ [SYNC] Paused state - staying paused');
            audioPlayer.pause();
            ignoreLocalEvents = false;
            isSyncing = false;
            ToastNotification.info(`Synced to: ${syncMsg.songName} (Paused)`);
        } else if (syncMsg.isPlaying) {
            // console.log('▶️ [SYNC] Playing state - starting playback');
            const playPromise = audioPlayer.play();

            if (playPromise !== undefined) {
                playPromise
                    .then(() => {
                        // console.log('✅ [SYNC] Playback synced successfully');
                        ignoreLocalEvents = false;
                        isSyncing = false;
                        ToastNotification.success(`🎵 Synced: ${syncMsg.songName}`);
                    })
                    .catch(err => {
                        console.error('❌ [SYNC] Play error:', err.message);
                        ignoreLocalEvents = false;
                        isSyncing = false;
                        ToastNotification.warning('Synced but autoplay blocked - click play');
                    });
            } else {
                ignoreLocalEvents = false;
                isSyncing = false;
            }
        } else {
            // console.log('⏹️ [SYNC] No playback state - staying idle');
            ignoreLocalEvents = false;
            isSyncing = false;
        }
    };

    // Wait for metadata to load before syncing
    if (audioPlayer.readyState >= 2) {
        // console.log('✅ [SYNC] Metadata already loaded - syncing immediately');
        syncAudio();
    } else {
        // console.log('⏳ [SYNC] Waiting for metadata to load...');

        const metadataHandler = () => {
            // console.log('✅ [SYNC] Metadata loaded - syncing now');
            syncAudio();
            audioPlayer.removeEventListener('loadedmetadata', metadataHandler);
        };

        audioPlayer.addEventListener('loadedmetadata', metadataHandler, {once: true});

        // Timeout fallback
        setTimeout(() => {
            audioPlayer.removeEventListener('loadedmetadata', metadataHandler);
            if (isSyncing) {
                console.warn('⚠️ [SYNC] Metadata timeout (5s) - attempting sync anyway');
                syncAudio();
            }
        }, 5000);
    }
}

// ==================== NEW: REQUEST PLAYBACK SYNC ====================
function requestPlaybackSync() {
    if (!stompClient || !stompClient.connected) {
        console.error('❌ [SYNC] Cannot request sync - WebSocket not connected');
        return;
    }

    // ✅ FIX: Prevent concurrent sync requests
    if (syncRequestPending) {
        console.log('⏭️ [SYNC] Sync request already pending, queuing...');
        pendingSyncRequests++;
        return;
    }

    syncRequestPending = true;
    isSyncing = true;

    const syncRequest = {
        username: currentUsername, timestamp: Date.now(), roomName: currentRoomName
    };

    try {
        stompClient.send(`/app/music/chat/${currentRoomName}/playback/sync`, {}, JSON.stringify(syncRequest));

        // Set timeout in case no response
        syncTimeout = setTimeout(() => {
            if (isSyncing && !hasReceivedInitialSync) {
                console.log('⏱️ [SYNC] No sync response received - assuming no active playback');
                isSyncing = false;
            }

            // ✅ FIX: Clear pending flag and process queue
            syncRequestPending = false;

            // Process queued request if any
            if (pendingSyncRequests > 0) {
                pendingSyncRequests = 0; // Reset counter
                setTimeout(() => requestPlaybackSync(), 500); // Retry after delay
            }
        }, 5000);

    } catch (error) {
        console.error('❌ [SYNC] Error requesting sync:', error);
        isSyncing = false;
        syncRequestPending = false;
    }
}

function validateSession() {
    if (!jwtToken || !currentUsername || !currentRoomName) {
        console.error('❌ Invalid session');
        ToastNotification.error('Session expired. Redirecting to login...');
        setTimeout(() => {
            window.location.href = '/app/music/public/login';
        }, 2000);
        return false;
    }
    return true;
}

function playSong(song) {
    if (!validateSession()) return;
    if (!isOrganizer) {
        ToastNotification.warning('Only the organizer can play songs');
        return;
    }

    if (!stompClient || !stompClient.connected) {
        console.error('❌ WebSocket not connected');
        ToastNotification.error('WebSocket not connected. Please refresh.');
        return;
    }

    if (!currentUsername) {
        console.error('❌ Username not available:', currentUsername);
        ToastNotification.error('Username not found. Please refresh page.');
        return;
    }

    // ⭐ FIX 1: Reset ignoreLocalEvents flag before starting
    ignoreLocalEvents = false;

    // ⭐ FIX 2: Clear any pending audio load timeouts
    if (audioLoadTimeout) {
        clearTimeout(audioLoadTimeout);
        audioLoadTimeout = null;
    }

    // ⭐ REMOVE OR COMMENT OUT THIS LINE - No loading toast needed
    ToastNotification.info(`⏳ Loading ${song.songName}...`, 2500);

    const playbackMessage = {
        action: 'PLAY',
        songFileName: song.fileName,
        songName: song.songName,
        hero: song.hero,
        heroine: song.heroine,
        language: song.language,
        sender: currentUsername,
        controller: currentUsername,
        timestamp: 0
    };

    try {
        const messageJson = JSON.stringify(playbackMessage);
        stompClient.send(`/app/music/chat/${currentRoomName}/playback`, {}, messageJson);

        // console.log('📤 Sent PLAY command for:', song.songName);

        // ⭐ FIX 4: Add timeout to detect if WebSocket message was lost
        const playbackTimeout = setTimeout(() => {
            console.warn('⚠️ No playback response received within 5 seconds');
            ToastNotification.warning('Song loading timeout. Click again to retry.', 3000);
            ignoreLocalEvents = false; // Reset flag
        }, 5000);

        // Store timeout ID so handlePlaybackCommand can clear it
        window.currentPlaybackTimeout = playbackTimeout;

    } catch (error) {
        console.error('❌ Error sending playback message:', error);
        ToastNotification.error('Failed to play song');
        ignoreLocalEvents = false; // Reset flag on error
    }
}

// ==================== NEXT/PREVIOUS SONG NAVIGATION ====================
function playNextSong() {
    if (!isOrganizer) {
        ToastNotification.warning('Only the organizer can control playback');
        return;
    }

    let nextSong = null;
    let nextIndex = 0;

    // ==================== FAVORITES MODE (ISOLATED) ====================
    if (playlistMode === 'favorites') {
        const currentIndex = currentPlaylistIndex;

        if (currentIndex >= roomFavorites.length - 1) {
            ToastNotification.info('🎵 End of favorites playlist');
            return;
        }

        nextIndex = currentIndex + 1;
        nextSong = roomFavorites[nextIndex];
        currentFavoriteIndex = nextIndex;
        currentPlaylistIndex = nextIndex;
    }

    // ==================== SEARCH MODE (ISOLATED) ====================
    else if (playlistMode === 'search') {
        if (currentPlaylistIndex >= searchSongsList.length - 1) {
            ToastNotification.info('🎵 End of search results');
            return;
        }

        nextIndex = currentPlaylistIndex + 1;
        nextSong = searchSongsList[nextIndex];
        currentPlaylistIndex = nextIndex;
    }

    // ==================== GLOBAL MODE (WITH PAGINATION) ====================
    else if (playlistMode === 'all') {
        if (currentPlaylistIndex >= allSongsOnPage.length - 1) {
            // Check for next page
            if (currentPage < totalPages - 1) {
                loadNextPageAndPlay();
                return;
            } else {
                ToastNotification.info('🎵 End of playlist');
                return;
            }
        }

        nextIndex = currentPlaylistIndex + 1;
        nextSong = allSongsOnPage[nextIndex];
        currentPlaylistIndex = nextIndex;
    } else {
        ToastNotification.warning('No active playlist');
        return;
    }

    if (nextSong) {
        broadcastSkipCommand('NEXT', nextSong, nextIndex);
    }
}

function playPreviousSong() {
    if (!isOrganizer) {
        ToastNotification.warning('Only the organizer can control playback');
        return;
    }

    let prevSong = null;
    let prevIndex = 0;

    // ==================== FAVORITES MODE (ISOLATED) ====================
    if (playlistMode === 'favorites') {
        if (currentPlaylistIndex === 0) {
            ToastNotification.info('🎵 Already at first song in favorites');
            return;
        }

        prevIndex = currentPlaylistIndex - 1;
        prevSong = roomFavorites[prevIndex];
        currentFavoriteIndex = prevIndex;
        currentPlaylistIndex = prevIndex;
    }

    // ==================== SEARCH MODE (ISOLATED) ====================
    else if (playlistMode === 'search') {
        if (currentPlaylistIndex === 0) {
            ToastNotification.info('🎵 Already at first song in search results');
            return;
        }

        prevIndex = currentPlaylistIndex - 1;
        prevSong = searchSongsList[prevIndex];
        currentPlaylistIndex = prevIndex;
    }

    // ==================== GLOBAL MODE (WITH PAGINATION) ====================
    else if (playlistMode === 'all') {
        if (currentPlaylistIndex === 0) {
            // Check for previous page
            if (currentPage > 0) {
                loadPreviousPageAndPlay();
                return;
            } else {
                ToastNotification.info('🎵 Already at first song');
                return;
            }
        }

        prevIndex = currentPlaylistIndex - 1;
        prevSong = allSongsOnPage[prevIndex];
        currentPlaylistIndex = prevIndex;
    } else {
        ToastNotification.warning('No active playlist');
        return;
    }

    if (prevSong) {
        broadcastSkipCommand('PREVIOUS', prevSong, prevIndex);
    }
}

function broadcastSkipCommand(action, song = null, index = 0) {
    if (!stompClient || !stompClient.connected) {
        console.error('Cannot broadcast skip - WebSocket not connected');
        return;
    }

    const skipMessage = {
        action: action, song: song, index: index, controller: currentUsername, timestamp: Date.now()
    };

    try {
        stompClient.send(`/app/music/chat/${currentRoomName}/skip`, {}, JSON.stringify(skipMessage));

        if (song) {
            playSong(song);
        }
    } catch (error) {
        console.error('Error broadcasting skip:', error);
    }
}

function handleSkipCommand(message) {
    if (isOrganizer) {
        return;
    }

    if (message.controller === lastUserLeft) {
        console.warn("⏭️ Ignoring skip from user who just left:", message.controller);
        return;
    }

    const stillInRoom = currentParticipants.some(p => p.userName === message.controller);
    if (!stillInRoom) {
        console.warn("⏭️ Ignoring stale skip from user who left:", message.controller);
        return;
    }

    if (message.controller !== currentUsername && message.song) {
        const actionText = message.action === 'NEXT' ? 'skipped to next' : 'went to previous';
        ToastNotification.info(`${message.controller} ${actionText} song`, 3000);
    }

    if (message.song) {
        if (playlistMode === 'favorites' || isPlayingFavorites) {
            currentPlaylistIndex = message.index;
            currentFavoriteIndex = message.index;
        }
    }
}

function updatePlaybackButtons() {
    const prevBtn = document.getElementById('prevSongBtn');
    const nextBtn = document.getElementById('nextSongBtn');

    if (!prevBtn || !nextBtn) {
        console.warn('⚠️ Playback buttons not found');
        return;
    }

    if (isOrganizer) {
        prevBtn.disabled = false;
        nextBtn.disabled = false;
        prevBtn.title = 'Previous Song (Alt + ←)';
        nextBtn.title = 'Next Song (Alt + →)';
    } else {
        prevBtn.disabled = true;
        nextBtn.disabled = true;
        prevBtn.title = 'Only organizer can skip songs';
        nextBtn.title = 'Only organizer can skip songs';
    }
}

async function loadNextPageAndPlay() {
    if (currentPage >= totalPages - 1) {
        // console.log('🏁 Already on last page');
        ToastNotification.info('🎵 No more pages available');
        return;
    }

    // console.log('📀 Loading page', currentPage + 2, 'of', totalPages);
    ToastNotification.info(`Loading page ${currentPage + 2}...`);

    try {
        await loadSongsForPage(currentPage + 1);

        // Play first song of new page
        if (allSongsOnPage.length > 0) {
            currentPlaylistIndex = 0;
            playlistMode = 'all';
            const firstSong = allSongsOnPage[0];
            // console.log('✅ Page loaded, playing:', firstSong.songName);
            ToastNotification.success(`Playing: ${firstSong.songName}`);
            setTimeout(() => playSong(firstSong), DUPLICATE_EVENT_THRESHOLD);
        } else {
            console.warn('⚠️ Page loaded but no songs found');
            ToastNotification.warning('No songs on this page');
        }
    } catch (error) {
        console.error('❌ Error loading next page:', error);
        ToastNotification.error('Failed to load next page');
    }
}

async function loadPreviousPageAndPlay() {
    if (currentPage === 0) {
        // console.log('🏁 Already on first page');
        ToastNotification.info('🎵 Already on first page');
        return;
    }

    // console.log('📀 Loading page', currentPage, 'of', totalPages);
    ToastNotification.info(`Loading page ${currentPage}...`);

    try {
        await loadSongsForPage(currentPage - 1);

        // Play last song of new page
        if (allSongsOnPage.length > 0) {
            currentPlaylistIndex = allSongsOnPage.length - 1;
            playlistMode = 'all';
            const lastSong = allSongsOnPage[currentPlaylistIndex];
            // console.log('✅ Page loaded, playing:', lastSong.songName);
            ToastNotification.success(`Playing: ${lastSong.songName}`);
            setTimeout(() => playSong(lastSong), DUPLICATE_EVENT_THRESHOLD);
        } else {
            console.warn('⚠️ Page loaded but no songs found');
            ToastNotification.warning('No songs on this page');
        }
    } catch (error) {
        console.error('❌ Error loading previous page:', error);
        ToastNotification.error('Failed to load previous page');
    }
}

let heartbeatInterval = null;
let lastHeartbeat = Date.now();

// ==================== MODIFY: startHeartbeat() ====================
function startHeartbeat() {
    if (heartbeatInterval) clearInterval(heartbeatInterval);

    heartbeatInterval = setInterval(async () => {
        if (stompClient && stompClient.connected) {
            try {
                // ✅ Send WebSocket heartbeat
                stompClient.send(`/app/music/chat/${currentRoomName}/heartbeat`, {}, JSON.stringify({
                    username: currentUsername, timestamp: Date.now()
                }));
                lastHeartbeat = Date.now();

                // // ✅ NEW: Periodic session validation via HTTP
                // await validateSessionViaHttp();

            } catch (error) {
                console.error('❌ Heartbeat failed:', error);

                if (Date.now() - lastHeartbeat > 15000) {
                    console.error('❌ Connection appears dead, reconnecting...');
                    ToastNotification.warning('Connection lost. Reconnecting...');
                    try {
                        stompClient.disconnect();
                    } catch (e) {
                    }
                    setTimeout(() => connectWebSocket(jwtToken), 1000);
                }
            }
        }
    }, 10000); // Every 10 seconds
}

function cleanupIntervals() {
    if (heartbeatInterval) {
        clearInterval(heartbeatInterval);
        heartbeatInterval = null;
    }
    if (participantRefreshInterval) {
        clearInterval(participantRefreshInterval);
        participantRefreshInterval = null;
    }
}

// ✅ NEW: HTTP-based session validation
async function validateSessionViaHttp() {
    try {
        const response = await fetch('/app/music/chat/session/validate', {
            method: 'GET', headers: {
                'Authorization': `Bearer ${jwtToken}`, 'X-Requested-With': 'XMLHttpRequest'
            }
        });

        if (response.status === 401 || response.status === 403) {
            console.warn('🔒 Session expired during heartbeat check');
            handleSessionExpired('Session validation failed');
        }
    } catch (error) {
        // Network errors are OK - don't treat as session expiry
        console.warn('⚠️ Session validation request failed (network issue):', error.message);
    }
}


// ==================== WEBSOCKET CONNECTION ====================
function connectWebSocket(token) {
    // ✅ FIX: Check reconnection limit
    if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
        ToastNotification.error('Connection failed after multiple attempts. Please refresh the page.', 0);
        cleanupIntervals();
        return;
    }

    reconnectAttempts++;
    console.log(`🔄 Connection attempt ${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS}`);

    const socket = new SockJS('/app/music/ws');
    stompClient = Stomp.over(socket);

    stompClient.debug = null;

    // Add connection timeout
    let connectionTimeout = setTimeout(() => {
        console.error('❌ WebSocket connection timeout');
        ToastNotification.error('Connection timeout. Retrying...');
        if (stompClient) {
            try {
                stompClient.disconnect();
            } catch (e) {
            }
        }
        setTimeout(() => connectWebSocket(token), 2000);
    }, 10000); // 10 second timeout

    stompClient.connect({'Authorization': `Bearer ${token}`}, (frame) => {
        clearTimeout(connectionTimeout);
        // console.log('✅ Connected to WebSocket');
        reconnectAttempts = 0;
        lastSuccessfulConnection = Date.now();

        ToastNotification.success('Connected to chat room');
        startHeartbeat();
        // Subscribe to all channels...
        // (keep all existing subscriptions)

        // Chat messages
        stompClient.subscribe(`/topic/chat/${currentRoomName}`, (message) => {
            const msg = JSON.parse(message.body);
            if (msg.type === "LEAVE") {
                lastUserLeft = msg.sender;
                // ✅ FIX: Rate limit leave notifications
                if (toastRateLimiter.canShow('participant-leave')) {
                    ToastNotification.info(`${msg.sender} left the room`);
                }
                return;
            }

            if (msg.type === "JOIN") {
                // ✅ FIX: Rate limit join notifications
                if (toastRateLimiter.canShow('participant-join')) {
                    ToastNotification.success(`${msg.sender} joined the room`);
                }
                return;
            }
            if (msg.type === "CHAT") {
                displayMessage(msg);
            }
        });

        // Playback commands
        stompClient.subscribe(`/topic/chat/${currentRoomName}/playback`, (message) => {
            const playbackMsg = JSON.parse(message.body);
            handlePlaybackCommand(playbackMsg);
        });

        // ⭐ CRITICAL: Subscribe to playback sync state responses
        stompClient.subscribe(`/topic/chat/${currentRoomName}/playback/state`, (message) => {
            // console.log('📨 [SYNC] Received sync state message');
            const syncMsg = JSON.parse(message.body);
            handlePlaybackSyncState(syncMsg);
        });

        // Typing, Skip, Participants, Favorites (keep existing subscriptions)
        stompClient.subscribe(`/topic/chat/${currentRoomName}/typing`, (message) => {
            const typingData = JSON.parse(message.body);
            handleTypingIndicator(typingData);
        });

        stompClient.subscribe(`/topic/chat/${currentRoomName}/skip`, (message) => {
            const skipMsg = JSON.parse(message.body);
            handleSkipCommand(skipMsg);
        });

        stompClient.subscribe(`/topic/chat/${currentRoomName}/participants`, (message) => {
            const participants = JSON.parse(message.body);
            updateParticipantsDisplay(participants);
        });

        stompClient.subscribe(`/topic/chat/${currentRoomName}/favorites`, (message) => {
            const favoritesMsg = JSON.parse(message.body);
            handleFavoritesUpdate(favoritesMsg);
        });

        // Send JOIN message
        stompClient.send(`/app/music/chat/${currentRoomName}/addUser`, {}, JSON.stringify({
            sender: currentUsername, type: 'JOIN', content: `${currentUsername} joined the room`
        }));

        // Request current favorites state
        requestFavoritesSync();

        // ⭐ CRITICAL: Request playback sync for late joiners
        // Give server time to process JOIN, then sync
        // console.log('⏳ [SYNC] Scheduling playback sync in 1 second...');
        setTimeout(() => {
            // console.log('🚀 [SYNC] Initiating playback sync...');
            requestPlaybackSync();
        }, 1000);  // Increased delay to ensure JOIN is processed

        startParticipantRefreshInterval();
    }, (error) => {
        console.error('❌ WebSocket error:', error);

        // ✅ FIX: Clean up intervals on error
        cleanupIntervals();

        const msg = error?.headers?.message || error?.body || error?.toString?.() || '';

        if (msg.includes('401') || msg.includes('403') || msg.toLowerCase().includes('unauthorized') || msg.toLowerCase().includes('forbidden')) {
            handleSessionExpired('WebSocket authentication failed');
            return;
        }

        if (lastSuccessfulConnection && (Date.now() - lastSuccessfulConnection) > RECONNECT_RESET_TIME) {
            reconnectAttempts = 0;
            console.log('🔄 Resetting reconnect counter (last success > 1 min ago)');
        }

        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            ToastNotification.error(`Connection error. Reconnecting... (${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})`);

            const retryDelay = Math.min(3000 * Math.pow(2, reconnectAttempts - 1), 10000);

            setTimeout(() => {
                if (!logoutInProgress && (!stompClient || !stompClient.connected)) {
                    connectWebSocket(token);
                }
            }, retryDelay);
        } else {
            ToastNotification.error('Unable to connect. Please refresh the page.', 0);
        }
    });
}

function handleTypingIndicator(data) {
    const typingIndicator = document.getElementById("typingIndicator");
    const typingUserSpan = document.querySelector(".typing-user");

    if (data.sender === currentUsername) return;

    if (data.typing) {
        typingUserSpan.textContent = data.sender + " is typing";
        typingIndicator.style.display = "flex";
    } else {
        typingIndicator.style.display = "none";
    }
}

function requestFavoritesSync() {
    if (stompClient && stompClient.connected) {
        stompClient.send(`/app/music/chat/${currentRoomName}/favorites/sync`, {}, JSON.stringify({username: currentUsername}));
    }
}

function startParticipantRefreshInterval() {
    if (participantRefreshInterval) {
        clearInterval(participantRefreshInterval);
    }

    participantRefreshInterval = setInterval(() => {
        refreshParticipants();
    }, 5000);
}

async function refreshParticipants() {
    try {
        const response = await fetchWithTimeout(`/app/music/room/getRoom?roomName=${encodeURIComponent(currentRoomName)}`);


        if (response.ok) {
            const room = await response.json();
            if (room.participant) {
                updateParticipantsDisplay(room.participant);
            }
        }
    } catch (error) {
        console.error('Error refreshing participants:', error);
    }
}

// ==================== PARTICIPANTS DISPLAY ====================
function updateParticipantsDisplay(participants) {
    const participantsList = document.getElementById('participantsList');
    const participantCount = document.getElementById('participantCount');
    currentParticipants = participants;

    participantsList.innerHTML = '';
    participantCount.textContent = `${participants.length} / ${PAGE_DATA.totalCount} participants`;

    const currentOrganizerUser = participants.find(p => p.organizer)?.userName || null;

    // ⭐ CRITICAL FIX: Detect organizer change
    if (lastKnownOrganizer !== null && lastKnownOrganizer !== currentOrganizerUser) {
        console.warn("🎭 ORGANIZER CHANGED:", lastKnownOrganizer, "→", currentOrganizerUser);

        // Check if previous organizer left
        const previousOrganizerStillExists = participants.some(p => p.userName === lastKnownOrganizer);

        if (!previousOrganizerStillExists) {
            console.warn("🎵 Previous organizer LEFT — resetting audio for all participants");
            resetAudioPlayer();

            // ⭐ NEW: If we're NOT the new organizer, request sync from new state
            if (!isOrganizer && currentOrganizerUser !== currentUsername) {
                console.log("🔄 Requesting fresh sync after organizer change");
                setTimeout(() => requestPlaybackSync(), DUPLICATE_EVENT_THRESHOLD);
            }
        }
    }

    lastKnownOrganizer = currentOrganizerUser;

    participants.forEach(p => {
        const item = document.createElement('div');
        item.className = 'participant-item';

        let userColor, darkerColor;
        if (p.userName === currentUsername) {
            userColor = currentUserColor;
            darkerColor = currentUserDarkerColor;
        } else {
            userColor = getColorForUser(p.userName);
            darkerColor = getDarkerShade(userColor);
        }

        item.innerHTML = `
            <div class="participant-name" style="background: linear-gradient(135deg, ${userColor}, ${darkerColor})">
                ${p.userName}
            </div>
            ${p.organizer ? '<span class="organizer-badge">Organizer</span>' : ''}
        `;
        participantsList.appendChild(item);

        if (p.userName === currentUsername) {
            const wasOrganizer = isOrganizer;
            isOrganizer = p.organizer;

            if (wasOrganizer !== isOrganizer) {
                console.log('🎭 User role changed from', wasOrganizer ? 'ORGANIZER' : 'PARTICIPANT', 'to', isOrganizer ? 'ORGANIZER' : 'PARTICIPANT');
                onRoleChange();

                if (isOrganizer) {
                    ToastNotification.success('🎉 You are now the organizer!');
                    // ⭐ NEW: Clear any stale playback state when becoming organizer
                    resetAudioPlayer();
                } else {
                    ToastNotification.info('You are now a participant');

                    // Reset audio and request sync
                    console.log('⬇️ Demoted to participant - resetting audio and syncing');
                    resetAudioPlayer();

                    setTimeout(() => {
                        if (!isOrganizer) {
                            console.log('🔄 Requesting sync as new participant');
                            requestPlaybackSync();
                        }
                    }, DUPLICATE_EVENT_THRESHOLD);
                }
            }
        }
    });
}

function getColorForUser(username) {
    if (!userColors[username]) {
        // Generate consistent color based on username hash
        let hash = 0;
        for (let i = 0; i < username.length; i++) {
            hash = username.charCodeAt(i) + ((hash << 5) - hash);
        }
        const colorIndex = Math.abs(hash) % colors.length;
        userColors[username] = colors[colorIndex];
    }
    return userColors[username];
}

function updatePermissionNotice() {
    const notice = document.getElementById('permissionNotice');
    if (!isOrganizer) {
        notice.style.display = 'block';
    } else {
        notice.style.display = 'none';
    }
}

function updateAudioControls() {
    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) return;

    if (isOrganizer) {
        audioPlayer.controls = true;
        const overlay = document.getElementById('controlsOverlay');
        if (overlay) overlay.remove();
    } else {
        audioPlayer.controls = true;
        let overlay = document.getElementById('controlsOverlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'controlsOverlay';
            overlay.style.position = 'absolute';
            overlay.style.pointerEvents = 'auto';
            overlay.style.top = '0';
            overlay.style.left = '0';
            overlay.style.width = '100%';
            overlay.style.height = '100%';
            overlay.style.zIndex = 50;
            overlay.style.background = 'transparent';
            overlay.title = 'Only the organizer can control playback';
            const wrapper = document.getElementById('audioPlayerWrapper') || document.body;
            wrapper.style.position = wrapper.style.position || 'relative';
            wrapper.appendChild(overlay);
        } else {
            overlay.style.display = 'block';
        }
    }
}

// ==================== CHAT MESSAGING ====================
function setReplyToMessage(messageId, sender, content) {
    replyingToMessage = {
        id: messageId, sender: sender, content: content
    };
    showReplyPreview();
}

function showReplyPreview() {
    if (!replyingToMessage) return;

    const chatInputContainer = document.querySelector('.chat-input-container');
    let existingPreview = document.getElementById('replyPreview');
    if (existingPreview) existingPreview.remove();

    const replyPreview = document.createElement('div');
    replyPreview.id = 'replyPreview';
    replyPreview.className = 'reply-preview';
    replyPreview.innerHTML = `
        <div class="reply-preview-content">
            <div class="reply-preview-header">
                <i class="bi bi-reply-fill"></i>
                <span>Replying to <strong>${escapeHtml(replyingToMessage.sender)}</strong></span>
            </div>
            <div class="reply-preview-text">${escapeHtml(replyingToMessage.content)}</div>
        </div>
        <button class="reply-preview-close" onclick="cancelReply()">
            <i class="bi bi-x"></i>
        </button>
    `;

    chatInputContainer.parentElement.insertBefore(replyPreview, chatInputContainer);
    document.getElementById('messageInput').focus();
}

function cancelReply() {
    replyingToMessage = null;
    const replyPreview = document.getElementById('replyPreview');
    if (replyPreview) replyPreview.remove();
}

function scrollToMessage(messageId) {
    const targetMessage = document.querySelector(`[data-message-id="${messageId}"]`);
    if (targetMessage) {
        targetMessage.scrollIntoView({behavior: 'smooth', block: 'center'});
        targetMessage.classList.add('message-highlight');
        setTimeout(() => targetMessage.classList.remove('message-highlight'), 2000);
    } else {
        ToastNotification.info('Original message not visible');
    }
}

// ==================== UPDATE YOUR sendMessage() FUNCTION ====================
// REPLACE your existing sendMessage() function with this:

function sendMessage() {
    const input = document.getElementById('messageInput');
    const content = input.value.trim();

    if (!content) {
        console.warn('Empty message');
        return;
    }

    if (!stompClient || !stompClient.connected) {
        console.error('❌ WebSocket not connected');
        ToastNotification.error('Not connected to chat. Please refresh.');
        return;
    }

    const chatMessage = {
        sender: currentUsername, content: content, type: 'CHAT', replyTo: replyingToMessage ? {
            id: replyingToMessage.id, sender: replyingToMessage.sender, content: replyingToMessage.content
        } : null, timestamp: Date.now()
    };

    try {
        stompClient.send(`/app/music/chat/${currentRoomName}/send`, {}, JSON.stringify(chatMessage));
        input.value = '';
        cancelReply();
    } catch (error) {
        console.error('❌ Error sending message:', error);
        ToastNotification.error('Error sending message');
    }
}

// ==================== UPDATE YOUR displayMessage() FUNCTION ====================

function displayMessage(message) {
    const chatMessages = document.getElementById('chatMessages');

    if (message.type === 'JOIN' || message.type === 'LEAVE') {
        return;
    }

    const isCurrentUser = message.sender === currentUsername;
    const messageId = message.timestamp || Date.now();

    const messageDiv = document.createElement('div');
    messageDiv.className = 'chat-bubble ' + (isCurrentUser ? 'my-message' : 'other-message');
    messageDiv.dataset.messageId = messageId;

    const time = new Date(message.timestamp || Date.now()).toLocaleTimeString('en-US', {
        hour: '2-digit', minute: '2-digit'
    });

    let replyHTML = '';
    if (message.replyTo) {
        replyHTML = `
            <div class="reply-reference" data-scroll-to="${message.replyTo.id}">
                <div class="reply-reference-header">
                    <i class="bi bi-reply-fill"></i>
                    <span>${escapeHtml(message.replyTo.sender)}</span>
                </div>
                <div class="reply-reference-text">${escapeHtml(message.replyTo.content)}</div>
            </div>
        `;
    }

    messageDiv.innerHTML = `
        <div class="chat-meta">
            <span class="sender">${escapeHtml(message.sender)}</span>
            <span class="time">${time}</span>
        </div>
        ${replyHTML}
        <div class="bubble-text">
            ${escapeHtml(message.content)}
        </div>
        <div class="message-actions">
            <button class="message-action-btn reply-btn" 
                    data-message-id="${messageId}"
                    data-sender="${escapeHtml(message.sender)}"
                    data-content="${escapeHtml(message.content)}"
                    title="Reply">
                <i class="bi bi-reply"></i>
            </button>
        </div>
    `;

    // ✅ Add event listener for reply button
    const replyBtn = messageDiv.querySelector('.reply-btn');
    if (replyBtn) {
        replyBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            const msgId = this.dataset.messageId;
            const sender = this.dataset.sender;
            const content = this.dataset.content;
            setReplyToMessage(msgId, sender, content);
        });
    }

    // ✅ Add event listener for reply reference (clicking on quoted message)
    const replyReference = messageDiv.querySelector('.reply-reference');
    if (replyReference) {
        replyReference.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            const targetId = this.dataset.scrollTo;
            scrollToMessage(targetId);
        });
    }

    chatMessages.appendChild(messageDiv);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// ==================== ADD ESCAPE KEY HANDLER ====================

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && replyingToMessage) {
        cancelReply();
    }
});

// ==================== SONG MANAGEMENT ====================
function displaySongs(songs) {
    allSongsOnPage = songs;
    const songList = document.getElementById('songList');
    songList.innerHTML = '';

    if (!songs || songs.length === 0) {
        showEmptyState();
        return;
    }

    songs.forEach((song, index) => {
        const songItem = document.createElement('div');
        songItem.className = 'song-item';

        // Store all song data as dataset attributes
        songItem.dataset.filename = song.fileName;
        songItem.dataset.songname = song.songName;
        songItem.dataset.hero = song.hero || '';
        songItem.dataset.singer = song.singer || '';
        songItem.dataset.movie = song.movie || '';
        songItem.dataset.heroine = song.heroine || '';
        songItem.dataset.language = song.language || '';

        const isFavorite = roomFavorites.some(f => f.fileName === song.fileName);

        songItem.innerHTML = `
            <div class="song-item-content" onclick="handleSongClick(this.parentElement)">
                <div class="song-item-title">${escapeHtml(song.songName)} • ${escapeHtml(song.language)}</div>
                <div class="song-item-info">${escapeHtml(song.hero || song.singer)} • ${escapeHtml(song.movie || 'Unknown')} • ${escapeHtml(song.language || 'Unknown')}</div>
            </div>
            <button class="favorite-heart ${isFavorite ? 'bi bi-heart-fill' : 'bi bi-heart'}" 
                    data-filename="${song.fileName}"
                    title="Add to room favorites">
            </button>
        `;

        // Add event listener for heart button AFTER adding to DOM
        const heartBtn = songItem.querySelector('.favorite-heart');
        heartBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            handleFavoriteToggle(e.currentTarget);
        });

        songList.appendChild(songItem);
    });

    songList.scrollTop = 0;
}

function handleFavoriteToggle(heartButton) {
    const songItem = heartButton.closest('.song-item');

    if (!songItem) {
        console.error('❌ Could not find song item');
        return;
    }

    const song = {
        fileName: songItem.dataset.filename,
        songName: songItem.dataset.songname,
        hero: songItem.dataset.hero,
        heroine: songItem.dataset.heroine,
        singer: songItem.dataset.singer,
        movie: songItem.dataset.movie,
        language: songItem.dataset.language
    };

    // console.log('💖 Toggling favorite for:', song.songName);
    toggleFavorite(song, heartButton);
}

/**
 * Determines the best playlist mode for a song
 * Priority: 1. Favorites, 2. Search (if available), 3. Global
 */
function determinePlaylistMode(song) {
    // Check favorites first (highest priority)
    const favIndex = roomFavorites.findIndex(s => s.fileName === song.fileName);
    if (favIndex !== -1) {
        return {
            mode: 'favorites', playlist: roomFavorites, index: favIndex
        };
    }

    // Check if currently in search mode with results
    if (searchSongsList.length > 0) {
        const searchIndex = searchSongsList.findIndex(s => s.fileName === song.fileName);
        if (searchIndex !== -1) {
            return {
                mode: 'search', playlist: searchSongsList, index: searchIndex
            };
        }
    }

    // Default to global playlist
    const globalIndex = allSongsOnPage.findIndex(s => s.fileName === song.fileName);
    return {
        mode: 'all', playlist: allSongsOnPage, index: globalIndex
    };
}

function handleSongClick(element) {
    if (!isOrganizer) {
        ToastNotification.warning('Only the organizer can play songs');
        return;
    }

    const song = {
        fileName: element.dataset.filename,
        songName: element.dataset.songname,
        hero: element.dataset.hero,
        heroine: element.dataset.heroine,
        singer: element.dataset.singer,
        movie: element.dataset.movie,
        language: element.dataset.language
    };

    // ✅ FIX: Use deterministic playlist selection
    const playlistInfo = determinePlaylistMode(song);

    isPlayingFavorites = (playlistInfo.mode === 'favorites');
    playlistMode = playlistInfo.mode;
    currentPlaylist = [...playlistInfo.playlist];
    currentPlaylistIndex = playlistInfo.index;

    if (playlistInfo.mode === 'favorites') {
        currentFavoriteIndex = playlistInfo.index;
        ToastNotification.info(`▶️ Playing from favorites (${playlistInfo.index + 1}/${roomFavorites.length})`);
    } else if (playlistInfo.mode === 'search') {
        ToastNotification.info(`▶️ Playing from search results (${playlistInfo.index + 1}/${searchSongsList.length})`);
    } else {
        ToastNotification.info(`▶️ Playing from global playlist (Page ${currentPage + 1})`);
    }

    playSong(song);
}

async function searchSongs() {
    const query = document.getElementById('searchInput').value.trim();
    if (!query) {
        ToastNotification.warning('Please enter a search term');
        return;
    }

    try {
        const response = await fetch(`/app/music/audio/searchSong?query=${encodeURIComponent(query)}`, {
            headers: {
                'Authorization': `Bearer ${jwtToken}`
            }
        });

        if (response.ok) {
            const songs = await response.json();
            displaySearchResults(songs);
        } else {
            ToastNotification.error('Error searching songs');
        }
    } catch (error) {
        console.error('Error searching songs:', error);
        ToastNotification.error('Search failed');
    }
}

function displaySearchResults(songs) {
    searchSongsList = songs;
    const searchResults = document.getElementById('searchResults');
    searchResults.innerHTML = '';

    if (songs.length === 0) {
        searchResults.innerHTML = '<div class="no-results">No songs found</div>';
        ToastNotification.info('No songs found');
        return;
    }

    ToastNotification.info(`Found ${songs.length} song(s)`);

    songs.forEach(song => {
        const songItem = document.createElement('div');
        songItem.className = 'song-item';

        // Store all song data as dataset attributes
        songItem.dataset.filename = song.fileName;
        songItem.dataset.songname = song.songName;
        songItem.dataset.hero = song.hero || '';
        songItem.dataset.heroine = song.heroine || '';
        songItem.dataset.singer = song.singer || '';
        songItem.dataset.movie = song.movie || '';
        songItem.dataset.language = song.language || '';

        const isFavorite = roomFavorites.some(f => f.fileName === song.fileName);

        songItem.innerHTML = `
            <div class="song-item-content" onclick="handleSearchSongClick(this.parentElement)">
                <div class="song-item-title">${escapeHtml(song.songName)} • ${escapeHtml(song.language)}</div>
                <div class="song-item-info">${escapeHtml(song.hero || song.singer)} • ${escapeHtml(song.movie || song.singer)} • ${escapeHtml(song.language || 'Unknown')}</div>
            </div>
            <button class="favorite-heart ${isFavorite ? 'bi bi-heart-fill' : 'bi bi-heart'}" 
                    data-filename="${song.fileName}"
                    title="Add to room favorites">
            </button>
        `;

        // Add event listener for heart button AFTER adding to DOM
        const heartBtn = songItem.querySelector('.favorite-heart');
        heartBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            handleFavoriteToggle(e.currentTarget);
        });

        searchResults.appendChild(songItem);
    });
}

function handleSearchSongClick(element) {
    if (!isOrganizer) {
        ToastNotification.warning('Only the organizer can play songs');
        return;
    }

    const song = {
        fileName: element.dataset.filename,
        songName: element.dataset.songname,
        hero: element.dataset.hero,
        heroine: element.dataset.heroine,
        singer: element.dataset.singer,
        movie: element.dataset.movie,
        language: element.dataset.language
    };

    // ⭐ ALWAYS use search mode when clicking from search results
    isPlayingFavorites = false;
    playlistMode = 'search';
    currentPlaylist = [...searchSongsList];
    currentPlaylistIndex = searchSongsList.findIndex(s => s.fileName === song.fileName);

    if (currentPlaylistIndex === -1) {
        currentPlaylist.push(song);
        currentPlaylistIndex = currentPlaylist.length - 1;
    }

    ToastNotification.info(`▶️ Playing from search results (${currentPlaylistIndex + 1}/${searchSongsList.length})`);
    playSong(song);
    closeSearchDrawer();
}


// ==================== UTILITY FUNCTIONS ====================


function getDarkerShade(color) {
    const colorMap = {
        '#1a1a1a': '#0a0a0a',
        '#2d2d2d': '#1a1a1a',
        '#3d3d3d': '#2d2d2d',
        '#505050': '#3d3d3d',
        '#636363': '#505050',
        '#767676': '#636363'
    };
    return colorMap[color] || '#000000';
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function handleEnter(event) {
    if (event.key === 'Enter') {
        sendMessage();
    }
}

function handleSearchEnter(event) {
    if (event.key === 'Enter') {
        searchSongs();
    }
}

function openSearchDrawer() {
    document.getElementById('searchDrawer').classList.add('open');
}

function closeSearchDrawer() {
    document.getElementById('searchDrawer').classList.remove('open');
    document.getElementById('searchInput').value = '';
    document.getElementById('searchResults').innerHTML = '';
}

function isReload() {
    const nav = performance.getEntriesByType("navigation")[0];
    return nav && nav.type === "reload";
}

let allowUnload = false;
let isClosing = false;


function sendFlagBeacon(fullLogout) {
    if (!jwtToken) {
        console.warn('⚠️ Missing JWT for beacon');
        return false;
    }

    // ✅ Use your new API endpoint
    const beaconUrl = `/app/music/room/update/session/flag`;

    const formData = new FormData();
    formData.append('token', jwtToken);
    formData.append('flag', fullLogout.toString());
    // ✅ Add timestamp for server-side validation
    formData.append('timestamp', Date.now().toString());

    try {
        const success = navigator.sendBeacon(beaconUrl, formData);
        console.log(success ? '✅ Flag beacon sent' : '❌ Beacon send failed');
        return success;
    } catch (error) {
        console.error('❌ Beacon error:', error);
        return false;
    }
}

// ==================== 2. PAGE RELOAD (F5 / Ctrl+R) ====================
// ✅ Exit room + Keep session + Redirect to dashboard
window.addEventListener('beforeunload', (event) => {
    const isCleanExit = sessionStorage.getItem('cleanExit') === 'true';
    if (isCleanExit) {
        sessionStorage.removeItem('cleanExit');
        return;
    }

    if (sessionExpired) {
        console.log('⏭️ Session expired - skipping beacon');
        return;
    }

    // ✅ ONLY show confirmation if NOT intentionally leaving
    if (!allowUnload) {
        // Show browser confirmation dialog
        const confirmationMessage = 'Reloading or closing this tab will log you out. Are you sure you want to leave?';
        event.preventDefault();
        event.returnValue = confirmationMessage; // Standard for most browsers
        return confirmationMessage; // For some older browsers
    }

    // If allowUnload is true, proceed with cleanup
    if (allowUnload) {
        console.log('🚪 Confirmed exit - proceeding with cleanup');
        isClosing = true;

        const nav = performance.getEntriesByType("navigation")[0];
        const isReloading = nav && nav.type === "reload";

        if (isReloading) {
            console.log('🔄 Page reload confirmed');
            sendFlagBeacon(false); // Exit room, keep session
        } else {
            console.log('🚪 Tab close confirmed');
            sendFlagBeacon(true); // Full logout
        }

        safeDisconnectWebSocket();
    }
});


// ==================== UNLOAD: FINAL CLEANUP ====================
window.addEventListener('unload', function () {
    if (sessionExpired) return;

    // Just ensure WebSocket is disconnected
    safeDisconnectWebSocket();

    console.log('🔚 Unload - WebSocket disconnected');
});

// ==================== PAGEHIDE: MOBILE/BFCache SUPPORT ====================
window.addEventListener('pagehide', function (event) {
    if (sessionExpired) return;

    // For mobile/back-forward cache
    if (event.persisted === false && !isClosing) {
        console.log('📱 Pagehide - disconnecting WebSocket');
        safeDisconnectWebSocket();
    }
});

function safeDisconnectWebSocket() {
    if (stompClient && stompClient.connected) {
        try {
            // Send LEAVE message for real-time notification
            stompClient.send(`/app/music/chat/${currentRoomName}/removeUser`, {}, JSON.stringify({
                sender: currentUsername, type: 'LEAVE', content: `${currentUsername} left the room`
            }));

            // ✅ Add small delay before disconnect to ensure message is sent
            setTimeout(() => {
                try {
                    stompClient.disconnect();
                    console.log('✅ WebSocket disconnected gracefully (LEAVE sent)');
                } catch (e) {
                    console.error('❌ WebSocket disconnect error:', e);
                }
            }, 100);

        } catch (e) {
            console.error('❌ Error sending LEAVE message:', e);
            // Try to disconnect anyway
            try {
                stompClient.disconnect();
            } catch (e2) {
            }
        }
    }

    if (heartbeatInterval) {
        clearInterval(heartbeatInterval);
        heartbeatInterval = null;
    }
}


async function handleBack() {
    if (isClosing) return;

    try {
        allowUnload = true;
        isClosing = true;

        // Stop participant refresh
        if (participantRefreshInterval) {
            clearInterval(participantRefreshInterval);
        }

        // ✅ STEP 1: Set intentionalLogout=false (back button)
        await fetch('/app/music/room/update/session/flag', {
            method: 'POST', headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            }, body: new URLSearchParams({
                token: jwtToken, flag: 'false'  // Keep session alive
            })
        });

        console.log('✅ Flag set to false (back button)');

        // ✅ STEP 2: Disconnect WebSocket (triggers backend cleanup)
        safeDisconnectWebSocket();

        // Mark clean exit
        sessionStorage.setItem('cleanExit', 'true');

    } catch (error) {
        console.error("❌ Error during back navigation:", error);
        // Still disconnect even if flag update fails
        safeDisconnectWebSocket();
    } finally {
        // ✅ STEP 3: Navigate to dashboard
        window.location.href = '/app/music/dashboard';
    }
}

// ==================== 4. LOGOUT BUTTON ====================
// ✅ Exit room + Full logout + Redirect to login
async function logout() {
    if (logoutInProgress) return;
    logoutInProgress = true;

    try {
        allowUnload = true;
        isClosing = true;

        // Stop participant refresh
        if (participantRefreshInterval) {
            clearInterval(participantRefreshInterval);
        }

        // ✅ STEP 1: Set intentionalLogout=true (full logout)
        await fetch('/app/music/room/update/session/flag', {
            method: 'POST', headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            }, body: new URLSearchParams({
                token: jwtToken, flag: 'true'  // Delete session
            })
        });

        console.log('✅ Flag set to true (logout)');

        // ✅ STEP 2: Send LEAVE message + Disconnect WebSocket
        safeDisconnectWebSocket();

        // Mark clean exit
        sessionStorage.setItem('cleanExit', 'true');

    } catch (error) {
        console.error('❌ Logout error:', error);
        // Still disconnect even if flag update fails
        safeDisconnectWebSocket();
    } finally {
        // ✅ STEP 3: Navigate to logout endpoint
        window.location.href = '/app/music/public/logout';
    }
}


document.addEventListener('keydown', (e) => {
    if (!isOrganizer) return;

    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

    if (e.altKey && e.key === 'ArrowLeft') {
        e.preventDefault();
        playPreviousSong();
    }

    if (e.altKey && e.key === 'ArrowRight') {
        e.preventDefault();
        playNextSong();
    }
});