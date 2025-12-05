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
let isClosing = false;
const userColors = {};
const colors = ['#1a1a1a', '#2d2d2d', '#3d3d3d', '#505050', '#636363', '#767676'];
let currentPage = 0;
let totalPages = 1;
let currentParticipants = [];
let lastUserLeft = null;
let boundAudioRole = null;
let logoutInProgress = false;
let lastKnownOrganizer = null;
let allSongsOnPage = [];
let searchSongsList = [];
// ==================== SHARED ROOM FAVORITES ====================
let roomFavorites = []; // Shared across all participants in the room
let isPlayingFavorites = false;
let currentFavoriteIndex = 0;

// ==================== ADD TO GLOBAL VARIABLES SECTION ====================
let currentPlaylist = [];
let currentPlaylistIndex = 0;
let playlistMode = null;

// ==================== TYPING INDICATOR ====================
let typing = false;
let typingTimeout;

let lastSyncRequest = 0;
let syncRequestCooldown = 2000; // 2 seconds

let syncRetryCount = 0;
const MAX_SYNC_RETRIES = 3;

function toggleFavorite(song, heartIcon) {
    const songId = song.fileName;
    const existingFavorite = roomFavorites.find(f => f.fileName === songId);

    if (existingFavorite) {
        // Remove from favorites
        const updatedFavorites = roomFavorites.filter(f => f.fileName !== songId);
        broadcastFavorites(updatedFavorites, 'REMOVE', song, currentUsername);
        ToastNotification.info(`Removed "${song.songName}" from room favorites`);
    } else {
        // Add to favorites with requester info
        const favoriteItem = {
            ...song,
            requestedBy: currentUsername,
            requestedAt: Date.now()
        };
        const updatedFavorites = [...roomFavorites, favoriteItem];
        broadcastFavorites(updatedFavorites, 'ADD', favoriteItem, currentUsername);
        ToastNotification.success(`Added "${song.songName}" to room favorites`);
    }
}


function sendTypingEvent(isTyping) {
    if (!stompClient || !stompClient.connected) return;

    const typingMsg = {
        sender: currentUsername,
        typing: isTyping
    };

    stompClient.send(
        `/app/music/chat/${currentRoomName}/typing`,
        {},
        JSON.stringify(typingMsg)
    );
}

// detect user typing
document.getElementById("messageInput").addEventListener("input", () => {
    if (!typing) {
        typing = true;
        sendTypingEvent(true);
    }

    clearTimeout(typingTimeout);
    typingTimeout = setTimeout(() => {
        typing = false;
        sendTypingEvent(false);
    }, 1200); // stop typing after 1.2 sec
});

function broadcastFavorites(favorites, action, song, username) {
    if (!stompClient || !stompClient.connected) {
        console.error('Cannot broadcast favorites - WebSocket not connected');
        return;
    }

    const message = {
        action: action, // 'ADD', 'REMOVE', 'SYNC'
        favorites: favorites,
        song: song,
        username: username,
        timestamp: Date.now()
    };

    try {
        stompClient.send(
            `/app/music/chat/${currentRoomName}/favorites`,
            {},
            JSON.stringify(message)
        );
        // // console.log('📤 Broadcast favorites:', action, song?.songName);
    } catch (error) {
        console.error('Error broadcasting favorites:', error);
    }
}

function handleFavoritesUpdate(message) {
    // console.log('📥 Received favorites update:', message);
    // console.log('   Action:', message.action);
    // console.log('   From:', message.username);
    // console.log('   Current user:', currentUsername);

    roomFavorites = message.favorites || [];
    updateFavoritesDisplay();
    updateAllHeartIcons();

    // Show toast notification for others (not the person who made the change)
    if (message.username !== currentUsername) {
        if (message.action === 'ADD' && message.song) {
            ToastNotification.info(`${message.username} added "${message.song.songName}" to favorites`, 3000);
        } else if (message.action === 'REMOVE' && message.song) {
            ToastNotification.info(`${message.username} removed "${message.song.songName}" from favorites`, 3000);
        } else if (message.action === 'CLEAR') {
            ToastNotification.warning(`${message.username} cleared all favorites`, 3000);
        } else if (message.action === 'SYNC') {
            // Silent sync, no notification needed
            // console.log('📋 Synced favorites list');
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
    // Update heart icons in main song list
    const songItems = document.querySelectorAll('.song-item');
    songItems.forEach(item => {
        const fileName = item.dataset.filename;
        const heartIcon = item.querySelector('.favorite-heart');
        if (heartIcon && fileName) {
            const isFavorite = roomFavorites.some(f => f.fileName === fileName);
            heartIcon.classList.remove('bi-heart', 'bi-heart-fill');
            heartIcon.classList.add(isFavorite ? 'bi-heart-fill' : 'bi-heart');
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

    const updatedFavorites = roomFavorites.filter(f => f.fileName !== fileName);
    broadcastFavorites(updatedFavorites, 'REMOVE', favorite, currentUsername);
    ToastNotification.info(`Removed "${favorite.songName}" from favorites`);
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

    ToastNotification.success(`Starting room favorites (${roomFavorites.length} songs)`);
    playSong(roomFavorites[currentFavoriteIndex]);
}

function playNextFavorite() {
    if (!isPlayingFavorites || !isOrganizer) return;

    currentFavoriteIndex++;

    if (currentFavoriteIndex >= roomFavorites.length) {
        isPlayingFavorites = false;
        currentFavoriteIndex = 0;
        ToastNotification.info('Room favorites playlist ended');
        return;
    }

    ToastNotification.info(`Playing ${currentFavoriteIndex + 1}/${roomFavorites.length} from favorites`);
    playSong(roomFavorites[currentFavoriteIndex]);
}

function stopFavoritesPlaylist() {
    isPlayingFavorites = false;
    currentFavoriteIndex = 0;
    ToastNotification.info('Stopped favorites playlist');
}

function openFavoritesDrawer() {
    document.getElementById('favoritesDrawer').classList.add('open');
}

function closeFavoritesDrawer() {
    document.getElementById('favoritesDrawer').classList.remove('open');
}

function clearAllFavorites() {
    if (roomFavorites.length === 0) return;

    if (confirm(`Are you sure you want to clear all ${roomFavorites.length} favorite songs? This will affect all participants.`)) {
        broadcastFavorites([], 'CLEAR', null, currentUsername);
        ToastNotification.success('Cleared all room favorites');
    }
}


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
        const playbackMessage = {
            action: 'PAUSE',
            timestamp: Math.floor(audioPlayer.currentTime * 1000),
            controller: currentUsername
        };
        // console.log('⏸️ [ORGANIZER] Sending PAUSE command:', playbackMessage);
        stompClient.send(`/app/music/chat/${currentRoomName}/playback`, {}, JSON.stringify(playbackMessage));
    }
};

const onOrganizerPlay = (e) => {
    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) return;
    if (!isOrganizer) return;
    if (!ignoreLocalEvents && e.isTrusted && stompClient?.connected && audioPlayer.src && currentSongData) {
        const playbackMessage = {
            action: 'RESUME',
            timestamp: Math.floor(audioPlayer.currentTime * 1000),
            controller: currentUsername,
            songFileName: currentSongData.songFileName,
            songName: currentSongData.songName,
            hero: currentSongData.hero,
            heroine: currentSongData.heroine,
            language: currentSongData.language
        };
        // console.log('▶️ [ORGANIZER] Sending RESUME command:', playbackMessage);
        stompClient.send(`/app/music/chat/${currentRoomName}/playback`, {}, JSON.stringify(playbackMessage));
    }
};

const onParticipantPlay = (e) => {
    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) return;
    if (isOrganizer) return;

    // Only prevent if user-initiated AND not during WebSocket sync
    if (!ignoreLocalEvents && e.isTrusted && audioPlayer.paused) {
        // console.log('🔒 [NON-ORGANIZER] Prevented manual play attempt (user initiated).');
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
        // console.log('🔒 [NON-ORGANIZER] Prevented manual pause attempt (user initiated).');
        e.preventDefault();
        audioPlayer.play().catch(err =>  console.log('Resume from prevented pause:', err));
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
    // console.log('🎛️ Rebound audio handlers for role:', role);
}

function onSongEnded() {
    // console.log('🎵 Song ended');

    if (!isOrganizer) return;

    const current = currentSongData?.songFileName;
    if (!current) return;

    let nextSong = null;
    let nextIndex = -1;

    // Priority 1: Check if current song is in favorites playlist
    const favIndex = roomFavorites.findIndex(s => s.fileName === current);
    if (favIndex !== -1 && favIndex < roomFavorites.length - 1) {
        // Playing from favorites - continue with next favorite
        nextIndex = favIndex + 1;
        nextSong = roomFavorites[nextIndex];
        currentFavoriteIndex = nextIndex;
        playlistMode = 'favorites';
        // console.log('📀 Auto-playing next song from favorites playlist');
    }
    // Priority 2: Check if playing from search results
    else if (playlistMode === 'search' && currentPlaylist.length > 0) {
        const searchIndex = currentPlaylist.findIndex(s => s.fileName === current);
        if (searchIndex !== -1 && searchIndex < currentPlaylist.length - 1) {
            nextIndex = searchIndex + 1;
            nextSong = currentPlaylist[nextIndex];
            currentPlaylistIndex = nextIndex;
            // console.log('📀 Auto-playing next song from search results');
        }
    }
    // Priority 3: Check if playing from main song list (current page)
    else if (playlistMode === 'all' && allSongsOnPage.length > 0) {
        const pageIndex = allSongsOnPage.findIndex(s => s.fileName === current);
        if (pageIndex !== -1 && pageIndex < allSongsOnPage.length - 1) {
            nextIndex = pageIndex + 1;
            nextSong = allSongsOnPage[nextIndex];
            currentPlaylistIndex = nextIndex;
            // console.log('📀 Auto-playing next song from current page');
        } else {
            // console.log('📀 Reached end of current page - no more songs to auto-play');
            ToastNotification.info('🎵 Playlist ended');
        }
    }
    // Priority 4: Fallback - try to find in any available list
    else {
        // Try to find current song in allSongsOnPage
        const pageIndex = allSongsOnPage.findIndex(s => s.fileName === current);
        if (pageIndex !== -1 && pageIndex < allSongsOnPage.length - 1) {
            nextIndex = pageIndex + 1;
            nextSong = allSongsOnPage[nextIndex];
            currentPlaylistIndex = nextIndex;
            playlistMode = 'all';
            // console.log('📀 Auto-playing next song from page (fallback)');
        }
    }

    // Play the next song if found
    if (nextSong) {
        ToastNotification.info(`▶️ Auto-playing: ${nextSong.songName}`);
        setTimeout(() => playSong(nextSong), 700);
    } else {
        // console.log('📀 No next song available - playlist ended');
    }
}

function onRoleChange() {
    updatePermissionNotice();
    syncAudioWrapperClass();
    updateAudioControls();
    updatePlaybackButtons(); // ADD THIS LINE

    const desired = isOrganizer ? 'organizer' : 'participant';
    if (boundAudioRole !== desired) {
        bindAudioHandlersForRole(desired);
    }

    if (!isOrganizer) {
        console.log('🔄 Role changed to participant - requesting sync');
        setTimeout(() => {
            requestPlaybackStateSync();
        }, 500);
    }
    // console.log('🎭 Role changed - isOrganizer:', isOrganizer);
}

function resetAudioOnOrganizerLeave() {
    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) return;

    // console.log("🎶 Organizer left - resetting audio");

    try {
        audioPlayer.pause();
    } catch (e) {
    }

    audioPlayer.src = "";
    audioPlayer.removeAttribute("src");

    document.getElementById('currentSongTitle').textContent = "No song playing";
    document.getElementById('currentSongDetails').textContent = "Select a song to play";

    currentSongData = null;
    ignoreLocalEvents = false;

    isPlayingFavorites = false;
    currentFavoriteIndex = 0;
}

// ==================== PAGE INITIALIZATION ====================
window.onload = function () {
    initializePage();
};

function initializePage() {
    currentRoomName = PAGE_DATA.roomName;
    currentUsername = PAGE_DATA.username;
    jwtToken = PAGE_DATA.jwtToken;
    currentUserColor = PAGE_DATA.userColor;
    currentUserDarkerColor = PAGE_DATA.darkerColor;
    isOrganizer = PAGE_DATA.isOrganizer;

    if (!jwtToken || !currentUsername || !currentRoomName) {
        console.error('Missing required data:', {jwtToken, currentUsername, currentRoomName});
        window.location.href = '/app/music/public/login';
        return;
    }

    // console.log('✅ Initialized chat for:', currentUsername, 'in room:', currentRoomName);
    // console.log('✅ Is organizer:', isOrganizer);

    updatePermissionNotice();
    setupAudioPlayerListeners();
    connectWebSocket(jwtToken);
    loadSongsForPage(0);
    initializeToastNotifications();
}

// ==================== PAGINATION FUNCTIONS ====================
async function loadSongsForPage(pageNumber) {
    if (isLoadingSongs) return;

    isLoadingSongs = true;
    showLoadingState();

    try {
        const response = await fetch(
            `/app/music/audio/fetchAllSongs?page=${pageNumber}&size=10`,
            {headers: {'Authorization': `Bearer ${jwtToken}`}}
        );

        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

        const data = await response.json();
        const pageData = data.page || data;

        currentPage = pageData.number ?? pageNumber;
        totalPages = pageData.totalPages ?? 1;

        displaySongs(data.content || []);
        updatePaginationUI();

    } catch (error) {
        console.error('Error loading songs:', error);
        ToastNotification.error('Failed to load songs');
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
    const displayPage = currentPage + 1;

    document.querySelector('.current-page').textContent = displayPage;
    document.querySelector('.total-pages').textContent = totalPages;
    document.getElementById('currentPageNum').textContent = displayPage;
    document.getElementById('totalPagesNum').textContent = totalPages;

    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');

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
const ToastNotification = {
    show: function (message, type = 'info', duration = 3000) {
        // console.log(`🔔 [TOAST] Attempting to show toast: [${type}] ${message}`);

        const toastContainer = document.getElementById('toastContainer');
        if (!toastContainer) {
            console.error('❌ [TOAST] Toast container not found!');
            return;
        }

        // console.log('✅ [TOAST] Toast container found:', toastContainer);

        const toastDiv = document.createElement('div');
        toastDiv.className = `toast ${type}`;

        // Add inline styles to ensure visibility (debugging)
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

        // Set background based on type
        if (type === 'success') toastDiv.style.backgroundColor = '#4caf50';
        else if (type === 'error') toastDiv.style.backgroundColor = '#f44336';
        else if (type === 'info') toastDiv.style.backgroundColor = '#2196F3';
        else if (type === 'warning') toastDiv.style.backgroundColor = '#ff9800';
        else toastDiv.style.backgroundColor = '#323232';

        const icons = {
            success: '✓',
            error: '✕',
            info: 'ℹ',
            warning: '⚠'
        };

        toastDiv.innerHTML = `
            <span class="toast-icon" style="font-size: 18px; flex-shrink: 0; font-weight: bold;">${icons[type] || icons.info}</span>
            <span class="toast-message" style="flex: 1; line-height: 1.4; word-break: break-word;">${message}</span>
            <button class="toast-close" style="background: none; border: none; color: white; cursor: pointer; font-size: 20px; padding: 0; flex-shrink: 0; width: 24px; height: 24px;">×</button>
        `;

        toastContainer.appendChild(toastDiv);
        // console.log('✅ [TOAST] Toast added to DOM, element:', toastDiv);
        // console.log('✅ [TOAST] Toast container children count:', toastContainer.children.length);

        toastDiv.querySelector('.toast-close').addEventListener('click', () => {
            // console.log('🔔 [TOAST] Close button clicked');
            this.removeToast(toastDiv);
        });

        if (duration > 0) {
            setTimeout(() => {
                // console.log('🔔 [TOAST] Auto-removing toast after', duration, 'ms');
                this.removeToast(toastDiv);
            }, duration);
        }
    },

    removeToast: function (toastDiv) {
        // console.log('🔔 [TOAST] Removing toast');
        toastDiv.classList.add('removing');
        setTimeout(() => {
            toastDiv.remove();
            // console.log('✅ [TOAST] Toast removed from DOM');
        }, 300);
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
    if (toastContainer) {
        // console.log('✅ [INIT] Toast container found on page load');
        // console.log('   Position:', window.getComputedStyle(toastContainer).position);
        // console.log('   Z-index:', window.getComputedStyle(toastContainer).zIndex);
        // console.log('   Top:', window.getComputedStyle(toastContainer).top);
        // console.log('   Right:', window.getComputedStyle(toastContainer).right);
    } else {
        console.error('❌ [INIT] Toast container NOT found on page load!');
    }
});
// ==================== UPDATE CURRENT SONG DISPLAY ====================
function updateCurrentSongDisplay(songName, hero, language, movie, singer) {
    document.getElementById('currentSongTitle').textContent = songName;
    const details = [hero || movie, singer || movie, language]
        .filter(Boolean)
        .join(' • ');

    document.getElementById('currentSongDetails').textContent = details || 'Unknown details';
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
    updatePlaybackButtons(); // ADD THIS LINE
    bindAudioHandlersForRole(isOrganizer ? 'organizer' : 'participant');
    // console.log('🎵 Setting up audio player listeners for:', isOrganizer ? 'ORGANIZER' : 'NON-ORGANIZER');
}

// ==================== PLAYBACK HANDLING ====================
function handlePlaybackCommand(playbackMsg) {
    const audioPlayer = document.getElementById('audioPlayer');

    if (!audioPlayer) {
        console.error('❌ Audio player not found in handlePlaybackCommand!');
        return;
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

    // console.log('🔥 [' + currentUsername + '] Received playback command:', playbackMsg.action, 'from:', playbackMsg.controller);

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

function updatePlaylistContext(song, index = 0, mode = null) {
    currentPlaylistIndex = index;
    playlistMode = mode;
    // console.log('📋 Playlist context updated:', {index, mode, song: song.songName});
}

function handlePlayCommand(audioPlayer, playbackMsg) {
    const newSrc = `/app/music/audio/public/streamSong/${playbackMsg.songFileName}`;

    currentSongData = {
        songFileName: playbackMsg.songFileName,
        songName: playbackMsg.songName,
        hero: playbackMsg.hero,
        heroine: playbackMsg.heroine,
        language: playbackMsg.language,
        movie: playbackMsg.movie
    };

    // console.log('🎵 Received PLAY command for song:', playbackMsg.songName);
    // console.log('   Audio source URL:', newSrc);
    updateCurrentSongDisplay(playbackMsg.songName, playbackMsg.hero, playbackMsg.language, playbackMsg.movie, playbackMsg.singer);

    const isOwnAction = playbackMsg.controller === currentUsername;
    if (isOwnAction) {
        ToastNotification.info(`🎵 Now Playing: ${playbackMsg.songName}`);
    } else {
        ToastNotification.info(`🎵 ${playbackMsg.controller} is playing: ${playbackMsg.songName}`);
    }

    let sourceChanged = true;
    try {
        const existingPath = audioPlayer.src ? new URL(audioPlayer.src).pathname : '';
        const newPath = new URL(newSrc, window.location.origin).pathname;
        sourceChanged = existingPath !== newPath;
    } catch (err) {
        sourceChanged = audioPlayer.src !== newSrc;
    }

    // console.log('   Source changed:', sourceChanged);

    if (sourceChanged) {
        // console.log('   Setting new audio source...');
        audioPlayer.src = newSrc;
    }

    const startTime = playbackMsg.timestamp ? playbackMsg.timestamp / 1000 : 0;
    // console.log('   Start time:', startTime.toFixed(2), 'seconds');

    if (sourceChanged) {
        // console.log('   Waiting for metadata to load...');

        let metadataLoaded = false;

        const metadataHandler = () => {
            metadataLoaded = true;
            // console.log('   ✅ Metadata loaded');
            audioPlayer.currentTime = startTime;

            // console.log('   ▶️ Attempting to play from:', startTime.toFixed(2) + 's');
            const playPromise = audioPlayer.play();

            if (playPromise !== undefined) {
                playPromise
                    .then(() => {
                        // console.log('   ✅ Audio playback started successfully');
                        ignoreLocalEvents = false;
                    })
                    .catch(err => {
                        console.error('   ❌ Play error:', err.message);
                        ignoreLocalEvents = false;
                    });
            } else {
                ignoreLocalEvents = false;
            }

            audioPlayer.removeEventListener('loadedmetadata', metadataHandler);
            if (audioLoadTimeout) clearTimeout(audioLoadTimeout);
        };

        audioPlayer.addEventListener('loadedmetadata', metadataHandler, {once: true});

        audioLoadTimeout = setTimeout(() => {
            if (!metadataLoaded) {
                console.warn('   ⚠️ Metadata loading timeout (8s), attempting to play anyway');
                audioPlayer.removeEventListener('loadedmetadata', metadataHandler);

                audioPlayer.currentTime = startTime;
                const playPromise = audioPlayer.play();

                if (playPromise !== undefined) {
                    playPromise
                        .then(() => {
                            // console.log('   ✅ Audio playback started (after timeout)');
                            ignoreLocalEvents = false;
                        })
                        .catch(err => {
                            console.error('   ❌ Play error after timeout:', err.message);
                            ignoreLocalEvents = false;
                        });
                } else {
                    ignoreLocalEvents = false;
                }
            }
        }, 8000);
    } else {
        // console.log('   Same source, seeking and playing');
        audioPlayer.currentTime = startTime;

        const playPromise = audioPlayer.play();
        if (playPromise !== undefined) {
            playPromise
                .then(() => {
                    // console.log('   ✅ Audio playback started');
                    ignoreLocalEvents = false;
                })
                .catch(err => {
                    console.error('   ❌ Play error:', err.message);
                    ignoreLocalEvents = false;
                });
        } else {
            ignoreLocalEvents = false;
        }
    }
}

function handlePauseCommand(audioPlayer, playbackMsg) {
    // console.log('⏸️ Pausing at timestamp:', (playbackMsg.timestamp / 1000).toFixed(2), 'seconds');
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
    // console.log('▶️ Resuming from timestamp:', (playbackMsg.timestamp / 1000).toFixed(2), 'seconds');
    if (playbackMsg.timestamp !== undefined) {
        audioPlayer.currentTime = playbackMsg.timestamp / 1000;
    }

    const playPromise = audioPlayer.play();
    if (playPromise !== undefined) {
        playPromise
            .then(() => {
                // console.log('   ✅ Resume successful');
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

function playSong(song) {
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

    // console.log('🎵 [ORGANIZER] Preparing to play song:', song.songName);
    // console.log('📤 Playback message to send:', playbackMessage);

    try {
        const messageJson = JSON.stringify(playbackMessage);
        stompClient.send(
            `/app/music/chat/${currentRoomName}/playback`,
            {},
            messageJson
        );

        // console.log('✅ Message sent successfully');
    } catch (error) {
        console.error('❌ Error sending playback message:', error);
        ToastNotification.error('Failed to play song');
    }
}

// ==================== ADD NEXT/PREVIOUS FUNCTIONS ====================
function playNextSong() {
    if (!isOrganizer) {
        ToastNotification.warning('Only the organizer can control playback');
        return;
    }

    let nextSong = null;
    let nextIndex = 0;

    if (playlistMode === 'favorites' && roomFavorites.length > 0) {
        // Playing from favorites
        const currentIndex = isPlayingFavorites ? currentFavoriteIndex : currentPlaylistIndex;

        if (currentIndex >= roomFavorites.length - 1) {
            ToastNotification.info('🎵 End of favorites playlist');
            return;
        }

        nextIndex = currentIndex + 1;
        nextSong = roomFavorites[nextIndex];

        if (isPlayingFavorites) {
            currentFavoriteIndex = nextIndex;
        }
        currentPlaylistIndex = nextIndex;

    } else if (playlistMode === 'search' && searchSongsList.length > 0) {
        // Playing from search results
        if (currentPlaylistIndex >= searchSongsList.length - 1) {
            ToastNotification.info('🎵 End of search results');
            return;
        }

        nextIndex = currentPlaylistIndex + 1;
        nextSong = searchSongsList[nextIndex];
        currentPlaylistIndex = nextIndex;

    } else if (playlistMode === 'all' && allSongsOnPage.length > 0) {
        // Playing from main list
        if (currentPlaylistIndex >= allSongsOnPage.length - 1) {
            ToastNotification.info('🎵 End of current page');
            return;
        }

        nextIndex = currentPlaylistIndex + 1;
        nextSong = allSongsOnPage[nextIndex];
        currentPlaylistIndex = nextIndex;

    } else {
        ToastNotification.warning('No playlist available to skip');
        return;
    }

    if (nextSong) {
        ToastNotification.info(`⏭️ Next: ${nextSong.songName}`);
        broadcastSkipCommand('NEXT', nextSong, nextIndex);
    } else {
        ToastNotification.warning('No next song available');
    }
}

function playPreviousSong() {
    if (!isOrganizer) {
        ToastNotification.warning('Only the organizer can control playback');
        return;
    }

    let prevSong = null;
    let prevIndex = 0;

    if (playlistMode === 'favorites' && roomFavorites.length > 0) {
        // Playing from favorites
        const currentIndex = isPlayingFavorites ? currentFavoriteIndex : currentPlaylistIndex;

        if (currentIndex === 0) {
            ToastNotification.info('🎵 Currently playing first song');
            return;
        }

        prevIndex = currentIndex - 1;
        prevSong = roomFavorites[prevIndex];

        if (isPlayingFavorites) {
            currentFavoriteIndex = prevIndex;
        }
        currentPlaylistIndex = prevIndex;

    } else if (playlistMode === 'search' && searchSongsList.length > 0) {
        // Playing from search results
        if (currentPlaylistIndex === 0) {
            ToastNotification.info('🎵 Currently at first song');
            return;
        }

        prevIndex = currentPlaylistIndex - 1;
        prevSong = searchSongsList[prevIndex];
        currentPlaylistIndex = prevIndex;

    } else if (playlistMode === 'all' && allSongsOnPage.length > 0) {
        // Playing from main list
        if (currentPlaylistIndex === 0) {
            ToastNotification.info('🎵 Currently at first song');
            return;
        }

        prevIndex = currentPlaylistIndex - 1;
        prevSong = allSongsOnPage[prevIndex];
        currentPlaylistIndex = prevIndex;

    } else {
        ToastNotification.warning('No playlist available to skip');
        return;
    }

    if (prevSong) {
        ToastNotification.info(`⏮️ Previous: ${prevSong.songName}`);
        broadcastSkipCommand('PREVIOUS', prevSong, prevIndex);
    } else {
        ToastNotification.warning('No previous song available');
    }
}

function broadcastSkipCommand(action, song = null, index = 0) {
    if (!stompClient || !stompClient.connected) {
        console.error('Cannot broadcast skip - WebSocket not connected');
        return;
    }

    const skipMessage = {
        action: action, // 'NEXT' or 'PREVIOUS'
        song: song,
        index: index,
        controller: currentUsername,
        timestamp: Date.now()
    };

    try {
        stompClient.send(
            `/app/music/chat/${currentRoomName}/skip`,
            {},
            JSON.stringify(skipMessage)
        );
        // console.log('📤 Broadcast skip command:', action);

        // If we have a song, play it immediately
        if (song) {
            playSong(song);
        }
    } catch (error) {
        console.error('Error broadcasting skip:', error);
    }
}

function handleSkipCommand(message) {
    // console.log('📥 Received skip command:', message);
    // console.log('   Action:', message.action);
    // console.log('   From:', message.controller);
    // console.log('   Current user:', currentUsername);

    // Only non-organizers should respond to skip commands
    if (isOrganizer) {
        // console.log('⏭️ Ignoring skip command as organizer');
        return;
    }

    // Ignore commands from users who left
    if (message.controller === lastUserLeft) {
        console.warn("⏭️ Ignoring skip from user who just left:", message.controller);
        return;
    }

    const stillInRoom = currentParticipants.some(p => p.userName === message.controller);
    if (!stillInRoom) {
        console.warn("⏭️ Ignoring stale skip from user who left:", message.controller);
        return;
    }

    // Show notification (only show for other users, not self)
    if (message.controller !== currentUsername && message.song) {
        const actionText = message.action === 'NEXT' ? 'skipped to next' : 'went to previous';
        ToastNotification.info(`${message.controller} ${actionText} song`, 3000);
        // console.log('✅ Toast notification shown');
    }

    // Update local state
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
        // Enable buttons for organizer
        prevBtn.disabled = false;
        nextBtn.disabled = false;
        prevBtn.title = 'Previous Song (Alt + ←)';
        nextBtn.title = 'Next Song (Alt + →)';
        // console.log('✅ Enabled playback buttons for organizer');
    } else {
        // Disable buttons for non-organizers
        prevBtn.disabled = true;
        nextBtn.disabled = true;
        prevBtn.title = 'Only organizer can skip songs';
        nextBtn.title = 'Only organizer can skip songs';
        // console.log('🔒 Disabled playback buttons for non-organizer');
    }
}

// ==================== WEBSOCKET CONNECTION ====================
function connectWebSocket(token) {
    const socket = new SockJS('/app/music/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect(
        {'Authorization': `Bearer ${token}`},
        (frame) => {
            // console.log('✅ Connected to WebSocket');
            ToastNotification.success('Connected to chat room');

            // Chat messages
            stompClient.subscribe(`/topic/chat/${currentRoomName}`, (message) => {
                const msg = JSON.parse(message.body);
                // console.log("🔥 CHAT message received:", msg);

                if (msg.type === "LEAVE") {
                    lastUserLeft = msg.sender;
                    // console.log("📌 User LEFT:", lastUserLeft);
                    ToastNotification.info(`${msg.sender} left the room`);
                    return;
                }

                if (msg.type === "JOIN") {
                    ToastNotification.success(`${msg.sender} joined the room`);
                    return;
                }

                if (msg.type === "CHAT") {
                    displayMessage(msg);
                }
            });

            // Playback commands
            stompClient.subscribe(`/topic/chat/${currentRoomName}/playback`, (message) => {
                const playbackMsg = JSON.parse(message.body);

                // ✅ NEW: If non-organizer receives PLAY command and has no current song, sync immediately
                if (!isOrganizer &&
                    playbackMsg.action === 'PLAY' &&
                    (!currentSongData || currentSongData.songFileName !== playbackMsg.songFileName)) {

                    console.log('🔄 New song detected - requesting immediate sync');

                    // Small delay to let backend update state
                    setTimeout(() => {
                        requestPlaybackStateSync();
                    }, 300);
                }
                handlePlaybackCommand(playbackMsg);
            });

            // Typing events
            stompClient.subscribe(`/topic/chat/${currentRoomName}/typing`, (message) => {
                const typingData = JSON.parse(message.body);
                handleTypingIndicator(typingData);
            });

            // Skip commands - VERIFY THIS EXISTS
            stompClient.subscribe(`/topic/chat/${currentRoomName}/skip`, (message) => {
                // console.log('📨 Raw skip message received:', message.body);
                const skipMsg = JSON.parse(message.body);
                handleSkipCommand(skipMsg);
            });

            // Participants updates
            stompClient.subscribe(`/topic/chat/${currentRoomName}/participants`, (message) => {
                const participants = JSON.parse(message.body);
                updateParticipantsDisplay(participants);
            });

            // Shared room favorites - VERIFY THIS EXISTS
            stompClient.subscribe(`/topic/chat/${currentRoomName}/favorites`, (message) => {
                // console.log('📨 Raw favorites message received:', message.body);
                const favoritesMsg = JSON.parse(message.body);
                handleFavoritesUpdate(favoritesMsg);
            });

            // Send JOIN message
            stompClient.send(`/app/music/chat/${currentRoomName}/addUser`, {}, JSON.stringify({
                sender: currentUsername,
                type: 'JOIN',
                content: `${currentUsername} joined the room`
            }));

            stompClient.subscribe(`/topic/chat/${currentRoomName}/playback/state`, (message) => {
                const stateMsg = JSON.parse(message.body);
                handlePlaybackStateSync(stateMsg);
            });

            setTimeout(() => {
                requestPlaybackStateSync();
            }, 1000 + Math.random() * 500);

            setTimeout(() => {
                if (!currentSongData && !isOrganizer) {
                    console.log('🔄 Second sync attempt - checking for active playback');
                    requestPlaybackStateSync();
                }
            }, 3500);
            // Request current favorites state
            requestFavoritesSync();

            startParticipantRefreshInterval();
        },
        (error) => {
            console.error('❌ WebSocket error:', error);
            ToastNotification.error('Connection error. Reconnecting...');
            setTimeout(() => {
                if (stompClient && !stompClient.connected) {
                    // console.log('🔄 Attempting to reconnect...');
                    connectWebSocket(token);
                }
            }, 500 + Math.random() * 700);
        }
    );
}

function requestPlaybackStateSync() {
    if (!stompClient || !stompClient.connected) {
        console.warn('⚠️ Cannot request playback sync - WebSocket not connected');
        return;
    }

    // Cooldown check to prevent spam
    const now = Date.now();
    if (now - lastSyncRequest < syncRequestCooldown) {
        console.log('⏳ Sync request on cooldown');
        return;
    }

    lastSyncRequest = now;

    const syncRequest = {
        requester: currentUsername,
        timestamp: now
    };

    try {
        stompClient.send(
            `/app/music/chat/${currentRoomName}/playback/sync`,
            {},
            JSON.stringify(syncRequest)
        );
        console.log('🔄 Requested playback state sync');
    } catch (error) {
        console.error('❌ Error requesting playback sync:', error);
    }
}

function handlePlaybackStateSync(stateMsg) {
    console.log('📥 Received playback state sync:', stateMsg);

    // Check if there's active playback
    if (!stateMsg.isPlaying || !stateMsg.songFileName) {
        console.log('ℹ️ No active playback to sync');
        if (syncRetryCount < MAX_SYNC_RETRIES) {
            syncRetryCount++;
            console.log(`🔄 Retrying sync (attempt ${syncRetryCount}/${MAX_SYNC_RETRIES})...`);

            setTimeout(() => {
                requestPlaybackStateSync();
            }, 2000 * syncRetryCount); // Exponential backoff: 2s, 4s, 6s
        } else {
            ToastNotification.info('No song currently playing');
            syncRetryCount = 0; // Reset counter
        }
        return;
    }

    syncRetryCount = 0;

    // If we're the organizer, ignore sync messages (we control playback)
    if (isOrganizer) {
        console.log('⏭️ Ignoring sync as organizer');
        return;
    }

    // ✅ ADD MISSING CHECK: If sync is from a user who left, ignore
    if (stateMsg.organizer === lastUserLeft) {
        console.warn('⏭️ Ignoring sync from user who left:', stateMsg.organizer);
        return;
    }

    const stillInRoom = currentParticipants.some(p => p.userName === stateMsg.organizer);
    if (!stillInRoom) {
        console.warn('⏭️ Ignoring stale sync from user who left:', stateMsg.organizer);
        return;
    }

    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) {
        console.error('❌ Audio player not found');
        return;
    }

    // Save current song data
    currentSongData = {
        songFileName: stateMsg.songFileName,
        songName: stateMsg.songName,
        hero: stateMsg.hero,
        heroine: stateMsg.heroine,
        language: stateMsg.language,
        movie: stateMsg.movie
    };

    // Update UI
    updateCurrentSongDisplay(
        stateMsg.songName,
        stateMsg.hero,
        stateMsg.language,
        stateMsg.movie,
        stateMsg.singer
    );

    // Show notification
    ToastNotification.info(`🎵 Syncing to: ${stateMsg.songName}`);

    // Set audio source
    const newSrc = `/app/music/audio/public/streamSong/${stateMsg.songFileName}`;

    ignoreLocalEvents = true;

    // Check if source needs changing
    let sourceChanged = true;
    try {
        const existingPath = audioPlayer.src ? new URL(audioPlayer.src).pathname : '';
        const newPath = new URL(newSrc, window.location.origin).pathname;
        sourceChanged = existingPath !== newPath;
    } catch (err) {
        sourceChanged = audioPlayer.src !== newSrc;
    }

    console.log('🔄 Source changed:', sourceChanged);

    if (sourceChanged) {
        audioPlayer.src = newSrc;
    }

    // Calculate current timestamp
    const currentTimestamp = stateMsg.timestamp + (Date.now() - stateMsg.serverTime);
    const startTime = currentTimestamp / 1000;

    console.log(`⏱️ Syncing to timestamp: ${startTime.toFixed(2)}s`);

    if (sourceChanged) {
        // Wait for metadata to load before seeking
        const metadataHandler = () => {
            audioPlayer.currentTime = startTime;

            if (stateMsg.isPaused) {
                audioPlayer.pause();
                console.log('⏸️ Synced to paused state');
            } else {
                const playPromise = audioPlayer.play();
                if (playPromise !== undefined) {
                    playPromise
                        .then(() => {
                            console.log('▶️ Synced playback started');
                            ignoreLocalEvents = false;
                        })
                        .catch(err => {
                            console.error('❌ Sync play error:', err.message);
                            ignoreLocalEvents = false;
                        });
                } else {
                    ignoreLocalEvents = false;
                }
            }

            audioPlayer.removeEventListener('loadedmetadata', metadataHandler);
        };

        audioPlayer.addEventListener('loadedmetadata', metadataHandler, {once: true});

        // Timeout fallback
        setTimeout(() => {
            audioPlayer.removeEventListener('loadedmetadata', metadataHandler);
            audioPlayer.currentTime = startTime;

            if (!stateMsg.isPaused) {
                audioPlayer.play().catch(err => console.error('Timeout play error:', err));
            }
            ignoreLocalEvents = false;
        }, 5000);

    } else {
        // Same source - just seek
        audioPlayer.currentTime = startTime;

        if (stateMsg.isPaused) {
            audioPlayer.pause();
            ignoreLocalEvents = false;
        } else {
            const playPromise = audioPlayer.play();
            if (playPromise !== undefined) {
                playPromise
                    .then(() => {
                        console.log('▶️ Synced playback (same source)');
                        ignoreLocalEvents = false;
                    })
                    .catch(err => {
                        console.error('❌ Sync play error:', err.message);
                        ignoreLocalEvents = false;
                    });
            } else {
                ignoreLocalEvents = false;
            }
        }
    }
}

function handleTypingIndicator(data) {
    const typingIndicator = document.getElementById("typingIndicator");
    const typingUserSpan = document.querySelector(".typing-user");

    if (data.sender === currentUsername) return; // do not show for self

    if (data.typing) {
        typingUserSpan.textContent = data.sender + " is typing";
        typingIndicator.style.display = "flex";
    } else {
        typingIndicator.style.display = "none";
    }
}

function requestFavoritesSync() {
    // Request current favorites state from server
    if (stompClient && stompClient.connected) {
        stompClient.send(
            `/app/music/chat/${currentRoomName}/favorites/sync`,
            {},
            JSON.stringify({username: currentUsername})
        );
        // console.log('🔄 Requested favorites sync');
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
        const response = await fetch(`/app/music/room/getRoom?roomName=${encodeURIComponent(currentRoomName)}`, {
            headers: {
                'Authorization': `Bearer ${jwtToken}`
            }
        });

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

    if (lastKnownOrganizer !== null) {
        const organizerStillExists = participants.some(p => p.userName === lastKnownOrganizer);

        if (!organizerStillExists) {
            console.warn("🎵 Organizer actually LEFT — resetting audio");
            resetAudioOnOrganizerLeave();
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
            userColor = getUserColor(p.userName);
            darkerColor = getDarkerShade(userColor);
        }

        item.innerHTML = `
            <div class="participant-name" style="background: linear-gradient(135deg, ${userColor}, ${darkerColor})">
                ${p.userName}
            </div>
            ${p.organizer ? '<span class="organizer-badge">Organizer</span>' : ''}
        `;
        participantsList.appendChild(item);

        // Check if current user's role changed
        if (p.userName === currentUsername) {
            const wasOrganizer = isOrganizer;
            isOrganizer = p.organizer;

            // Only trigger onRoleChange if role actually changed
            if (wasOrganizer !== isOrganizer) {
                console.log('🎭 User role changed from', wasOrganizer ? 'ORGANIZER' : 'PARTICIPANT',
                    'to', isOrganizer ? 'ORGANIZER' : 'PARTICIPANT');
                onRoleChange();

                // Show notification about role change
                if (isOrganizer) {
                    ToastNotification.success('🎉 You are now the organizer!');
                } else {
                    ToastNotification.info('You are now a participant');
                }
            }

            if (wasOrganizer && !isOrganizer) {
                resetAudioOnOrganizerLeave();
            }
        }
    });
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
        sender: currentUsername,
        content: content,
        type: 'CHAT'
    };

    try {
        stompClient.send(`/app/music/chat/${currentRoomName}/send`, {}, JSON.stringify(chatMessage));
        input.value = '';
        // console.log('✅ Message sent:', content);
    } catch (error) {
        console.error('❌ Error sending message:', error);
        ToastNotification.error('Error sending message');
    }
}

function displayMessage(message) {
    const chatMessages = document.getElementById('chatMessages');

    if (message.type === 'JOIN' || message.type === 'LEAVE') {
        return;
    }

    const isCurrentUser = message.sender === currentUsername;

    const messageDiv = document.createElement('div');
    messageDiv.className = 'chat-bubble ' + (isCurrentUser ? 'my-message' : 'other-message');

    const time = new Date().toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit'
    });

    messageDiv.innerHTML = `
        <div class="chat-meta">
            <span class="sender">${message.sender}</span>
            <span class="time">${time}</span>
        </div>
        <div class="bubble-text">
            ${escapeHtml(message.content)}
        </div>
    `;

    chatMessages.appendChild(messageDiv);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

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
        songItem.dataset.filename = song.fileName;
        songItem.dataset.songname = song.songName;
        songItem.dataset.hero = song.hero;
        songItem.dataset.singer = song.singer;
        songItem.dataset.movie = song.movie;
        songItem.dataset.heroine = song.heroine;
        songItem.dataset.language = song.language;

        const isFavorite = roomFavorites.some(f => f.fileName === song.fileName);

        songItem.innerHTML = `
            <div class="song-item-content" onclick="handleSongClick(this.parentElement)">
                <div class="song-item-title">${song.songName} • ${song.language}</div>
                <div class="song-item-info">${song.hero || song.singer} • ${song.movie || 'Unknown'} • ${song.language || 'Unknown'}</div>
            </div>
            <button class="favorite-heart ${isFavorite ? 'bi bi-heart-fill' : 'bi bi-heart'}" 
                    onclick="event.stopPropagation(); toggleFavorite({
                        fileName: '${song.fileName}',
                        songName: '${song.songName}',
                        hero: '${song.hero || ''}',
                        heroine: '${song.heroine || ''}',
                        singer: '${song.singer || ''}',
                        movie: '${song.movie || ''}',
                        language: '${song.language || ''}'
                    }, this)"
                    title="Add to room favorites">
            </button>
        `;

        songList.appendChild(songItem);
    });

    songList.scrollTop = 0;
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

    if (isPlayingFavorites) {
        stopFavoritesPlaylist();
    }

    // Check if song is from favorites list
    const favIndex = roomFavorites.findIndex(s => s.fileName === song.fileName);
    if (favIndex !== -1) {
        // Song is in favorites - set context to favorites
        playlistMode = 'favorites';
        currentPlaylist = [...roomFavorites];
        currentPlaylistIndex = favIndex;
        currentFavoriteIndex = favIndex;
        // console.log('🎵 Playing from favorites context');
    } else {
        // Song is from main list - set context to all songs
        playlistMode = 'all';
        currentPlaylist = [...allSongsOnPage];
        currentPlaylistIndex = allSongsOnPage.findIndex(s => s.fileName === song.fileName);

        // If not found in current page, add it
        if (currentPlaylistIndex === -1) {
            currentPlaylist.push(song);
            currentPlaylistIndex = currentPlaylist.length - 1;
        }
        // console.log('🎵 Playing from main list context');
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
        songItem.dataset.filename = song.fileName;
        songItem.dataset.songname = song.songName;
        songItem.dataset.hero = song.hero;
        songItem.dataset.heroine = song.heroine;
        songItem.dataset.singer = song.singer;
        songItem.dataset.movie = song.movie;
        songItem.dataset.language = song.language;

        const isFavorite = roomFavorites.some(f => f.fileName === song.fileName);

        songItem.innerHTML = `
            <div class="song-item-content" onclick="handleSearchSongClick(this.parentElement)">
                <div class="song-item-title">${song.songName} • ${song.language}</div>
                <div class="song-item-info">${song.hero || song.singer} • ${song.movie || song.singer} • ${song.language || 'Unknown'}</div>
            </div>
            <button class="favorite-heart ${isFavorite ? 'bi bi-heart-fill' : 'bi bi-heart'}" 
                    onclick="event.stopPropagation(); toggleFavorite({
                        fileName: '${song.fileName}',
                        songName: '${song.songName}',
                        hero: '${song.hero || ''}',
                        heroine: '${song.heroine || ''}',
                        singer: '${song.singer || ''}',
                        movie: '${song.movie || ''}',
                        language: '${song.language || ''}'
                    }, this)"
                    title="Add to room favorites">
            </button>
        `;

        searchResults.appendChild(songItem);
    });
}

// ==================== NEW: Handle Search Song Click ====================
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

    if (isPlayingFavorites) {
        stopFavoritesPlaylist();
    }

    // Check if song is from favorites list
    const favIndex = roomFavorites.findIndex(s => s.fileName === song.fileName);
    if (favIndex !== -1) {
        // Song is in favorites - set context to favorites
        playlistMode = 'favorites';
        currentPlaylist = [...roomFavorites];
        currentPlaylistIndex = favIndex;
        currentFavoriteIndex = favIndex;
        // console.log('🎵 Playing from favorites context (via search)');
    } else {
        // Set context to search results
        playlistMode = 'search';
        currentPlaylist = [...searchSongsList];
        currentPlaylistIndex = searchSongsList.findIndex(s => s.fileName === song.fileName);

        if (currentPlaylistIndex === -1) {
            currentPlaylist.push(song);
            currentPlaylistIndex = currentPlaylist.length - 1;
        }
        // console.log('🎵 Playing from search results context');
    }

    playSong(song);
    closeSearchDrawer();
}

// ==================== UTILITY FUNCTIONS ====================
function getUserColor(username) {
    if (!userColors[username]) {
        userColors[username] = colors[Object.keys(userColors).length % colors.length];
    }
    return userColors[username];
}

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

window.addEventListener('beforeunload', (event) => {
    if (isReload()) {
        allowUnload = true;
    }
    if (!allowUnload) {
        event.preventDefault();
        event.returnValue = '';
    }
});

window.addEventListener('unload', function () {
    handleTabClose();
});

window.addEventListener('pagehide', function (event) {
    if (event.persisted === false) {
        handleTabClose();
    }
});

// ==================== HANDLE TAB CLOSE ====================
function handleTabClose() {
    // console.log('📌 Tab/Window is closing - Initiating logout...');

    if (isReload()) {
        // console.log("🔄 Page reload detected — skipping LEAVE + logout");
        return;
    }

    if (isClosing) {
        // console.log('⚠️ Already closing, skipping duplicate handleTabClose');
        return;
    }
    isClosing = true;

    if (participantRefreshInterval) {
        clearInterval(participantRefreshInterval);
        // console.log('✅ Cleared participant refresh interval');
    }

    safeDisconnectWebSocket();
}

function safeDisconnectWebSocket() {
    if (stompClient && stompClient.connected) {
        try {
            stompClient.send(`/app/music/chat/${currentRoomName}/removeUser`, {}, JSON.stringify({
                sender: currentUsername,
                type: 'LEAVE',
                content: `${currentUsername} left the room`
            }));
            stompClient.disconnect();
        } catch (_) {
        }
    }
}

// ==================== LOGOUT BUTTON HANDLER ====================
async function handleBack() {
    try {
        allowUnload = true;
        isClosing = true;

        if (participantRefreshInterval) clearInterval(participantRefreshInterval);

        safeDisconnectWebSocket();

        if (currentRoomName && currentUsername) {
            await fetch(`/app/music/room/leave?roomName=${encodeURIComponent(currentRoomName)}&username=${encodeURIComponent(currentUsername)}`, {
                method: 'DELETE',
                headers: {'Authorization': `Bearer ${jwtToken}`},
                keepalive: true
            });
        }

        await fetch('/app/music/room/clearRoomSession', {method: 'POST', keepalive: true});

    } finally {
        window.location.href = '/app/music/dashboard';
    }
}

async function logout() {
    if (logoutInProgress) return;
    logoutInProgress = true;

    allowUnload = true;
    isClosing = true;

    try {
        // 1️⃣ Notify WebSocket that user is leaving (only once)
        await sendLeaveOverWebSocket();


    } catch (err) {
        console.error("Logout error:", err);
    } finally {
        // 3️⃣ Backend will clear session + remove user from room + remove JWT cookie
        window.location.href = '/app/music/public/logout';
    }
}
function sendLeaveOverWebSocket() {
    return new Promise(resolve => {
        if (!stompClient || !stompClient.connected) {
            resolve();
            return;
        }

        try {
            stompClient.send(
                `/app/music/chat/${currentRoomName}/removeUser`,
                {},
                JSON.stringify({
                    sender: currentUsername,
                    type: 'LEAVE',
                    content: `${currentUsername} left the room`
                })
            );

            // Proper async disconnect
            stompClient.disconnect(() => {
                // console.log("🔌 WebSocket disconnected cleanly");
                resolve();
            });

        } catch (e) {
            console.error("WS disconnect error:", e);
            resolve();
        }
    });
}


document.addEventListener('keydown', (e) => {
    // Only organizer can use shortcuts
    if (!isOrganizer) return;

    // Don't trigger when typing in input fields
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

    // Alt + Left Arrow = Previous
    if (e.altKey && e.key === 'ArrowLeft') {
        e.preventDefault();
        playPreviousSong();
    }

    // Alt + Right Arrow = Next
    if (e.altKey && e.key === 'ArrowRight') {
        e.preventDefault();
        playNextSong();
    }
});