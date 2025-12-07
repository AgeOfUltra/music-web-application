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
let roomFavorites = [];
let isPlayingFavorites = false;
let currentFavoriteIndex = 0;

// ==================== PLAYLIST MANAGEMENT ====================
let currentPlaylist = [];
let currentPlaylistIndex = 0;
let playlistMode = null;

// ==================== TYPING INDICATOR ====================
let typing = false;
let typingTimeout;

// ==================== NEW: SYNC STATE TRACKING ====================
let isSyncing = false;
let syncTimeout = null;
let hasReceivedInitialSync = false;

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

    console.log('📤 Broadcasting favorites:', action, song?.songName);

    const message = {
        action: action,
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

        console.log('✅ Favorites broadcast successful');

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
    console.log('📥 Received favorites update:', message.action);

    roomFavorites = message.favorites || [];

    console.log('📋 Current favorites:', roomFavorites.map(f => f.songName));

    updateFavoritesDisplay();
    updateAllHeartIcons();

    if (message.username !== currentUsername) {
        if (message.action === 'ADD' && message.song) {
            ToastNotification.info(`${message.username} added "${message.song.songName}" to favorites`, 3000);
        } else if (message.action === 'REMOVE' && message.song) {
            ToastNotification.info(`${message.username} removed "${message.song.songName}" from favorites`, 3000);
        } else if (message.action === 'CLEAR') {
            ToastNotification.warning(`${message.username} cleared all favorites`, 3000);
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
    console.log('💖 Updating heart icons, favorites count:', roomFavorites.length);

    // Update heart icons in main song list
    const songItems = document.querySelectorAll('#songList .song-item');
    songItems.forEach(item => {
        const fileName = item.dataset.filename;
        const heartIcon = item.querySelector('.favorite-heart');
        if (heartIcon && fileName) {
            const isFavorite = roomFavorites.some(f => f.fileName === fileName);
            heartIcon.classList.remove('bi-heart', 'bi-heart-fill');
            heartIcon.classList.add(isFavorite ? 'bi-heart-fill' : 'bi-heart');
            console.log(`  - ${fileName}: ${isFavorite ? 'favorited' : 'not favorited'}`);
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
        const playbackMessage = {
            action: 'PAUSE',
            timestamp: Math.floor(audioPlayer.currentTime * 1000),
            controller: currentUsername
        };
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
        stompClient.send(`/app/music/chat/${currentRoomName}/playback`, {}, JSON.stringify(playbackMessage));
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
        } catch (_) {}
        ToastNotification.warning('Only the organizer can control playback');
    }
};

const onParticipantPause = (e) => {
    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) return;
    if (isOrganizer) return;
    if (!ignoreLocalEvents && e.isTrusted) {
        e.preventDefault();
        audioPlayer.play().catch(err => console.log('Resume from prevented pause:', err));
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

    const current = currentSongData?.songFileName;
    if (!current) return;

    let nextSong = null;
    let nextIndex = -1;

    const favIndex = roomFavorites.findIndex(s => s.fileName === current);
    if (favIndex !== -1 && favIndex < roomFavorites.length - 1) {
        nextIndex = favIndex + 1;
        nextSong = roomFavorites[nextIndex];
        currentFavoriteIndex = nextIndex;
        playlistMode = 'favorites';
    } else if (playlistMode === 'search' && currentPlaylist.length > 0) {
        const searchIndex = currentPlaylist.findIndex(s => s.fileName === current);
        if (searchIndex !== -1 && searchIndex < currentPlaylist.length - 1) {
            nextIndex = searchIndex + 1;
            nextSong = currentPlaylist[nextIndex];
            currentPlaylistIndex = nextIndex;
        }
    } else if (playlistMode === 'all' && allSongsOnPage.length > 0) {
        const pageIndex = allSongsOnPage.findIndex(s => s.fileName === current);
        if (pageIndex !== -1 && pageIndex < allSongsOnPage.length - 1) {
            nextIndex = pageIndex + 1;
            nextSong = allSongsOnPage[nextIndex];
            currentPlaylistIndex = nextIndex;
        } else {
            // ⭐ ADD THIS: Check for next page
            if (currentPage < totalPages - 1) {
                console.log('📀 End of page - loading next page for auto-play');
                loadNextPageAndPlay();
                return;
            }
            ToastNotification.info('🎵 Playlist ended');
        }
    }

    if (nextSong) {
        ToastNotification.info(`▶️ Auto-playing: ${nextSong.songName}`);
        setTimeout(() => playSong(nextSong), 700);
    }
}

function onRoleChange() {
    updatePermissionNotice();
    syncAudioWrapperClass();
    updateAudioControls();
    updatePlaybackButtons();

    const desired = isOrganizer ? 'organizer' : 'participant';
    if (boundAudioRole !== desired) {
        bindAudioHandlersForRole(desired);
    }
}

function resetAudioOnOrganizerLeave() {
    const audioPlayer = document.getElementById('audioPlayer');
    if (!audioPlayer) return;

    try {
        audioPlayer.pause();
    } catch (e) {}

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
    if (!toastContainer) {
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
    updatePlaybackButtons();
    bindAudioHandlersForRole(isOrganizer ? 'organizer' : 'participant');
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

    if (sourceChanged) {
        audioPlayer.src = newSrc;
    }

    const startTime = playbackMsg.timestamp ? playbackMsg.timestamp / 1000 : 0;

    if (sourceChanged) {
        let metadataLoaded = false;

        const metadataHandler = () => {
            metadataLoaded = true;
            audioPlayer.currentTime = startTime;

            const playPromise = audioPlayer.play();

            if (playPromise !== undefined) {
                playPromise
                    .then(() => {
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
        audioPlayer.currentTime = startTime;

        const playPromise = audioPlayer.play();
        if (playPromise !== undefined) {
            playPromise
                .then(() => {
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

// ==================== NEW: HANDLE PLAYBACK SYNC RESPONSE ====================
function handlePlaybackSyncState(syncMsg) {
    console.log('🔄 [SYNC] Received playback state:', syncMsg);

    // Mark that we've received initial sync
    hasReceivedInitialSync = true;

    // Clear any pending sync timeout
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

    // Check if there's valid playback state
    if (!syncMsg.valid || !syncMsg.isPlaying || !syncMsg.songFileName) {
        console.log('ℹ️ [SYNC] No active playback to sync');
        isSyncing = false;
        return;
    }

    // If organizer, don't sync (they control playback)
    if (isOrganizer) {
        console.log('⏭️ [SYNC] Skipping sync - user is organizer');
        isSyncing = false;
        return;
    }

    console.log('🎵 [SYNC] Syncing to active song:', syncMsg.songName);

    // Calculate adjusted timestamp accounting for network delay
    const serverTime = syncMsg.serverTime || Date.now();
    const clientTime = Date.now();
    const elapsedSinceServer = clientTime - serverTime;

    let adjustedTimestamp = syncMsg.timestamp || 0;

    // Only adjust if not paused
    if (!syncMsg.isPaused) {
        adjustedTimestamp += elapsedSinceServer;
    }

    console.log('⏱️ [SYNC] Timestamp adjustment:', {
        original: syncMsg.timestamp,
        adjusted: adjustedTimestamp,
        elapsed: elapsedSinceServer
    });

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

    // Update UI
    updateCurrentSongDisplay(
        syncMsg.songName,
        syncMsg.hero,
        syncMsg.language,
        syncMsg.movie,
        syncMsg.singer
    );

    // Build audio source URL
    const audioSrc = `/app/music/audio/public/streamSong/${syncMsg.songFileName}`;

    // Load and sync audio
    audioPlayer.src = audioSrc;

    const syncAudio = () => {
        const targetTime = adjustedTimestamp / 1000; // Convert to seconds
        audioPlayer.currentTime = targetTime;

        if (syncMsg.isPaused) {
            console.log('⏸️ [SYNC] Paused state - staying paused');
            audioPlayer.pause();
            ignoreLocalEvents = false;
            isSyncing = false;
        } else {
            console.log('▶️ [SYNC] Playing state - starting playback');
            const playPromise = audioPlayer.play();

            if (playPromise !== undefined) {
                playPromise
                    .then(() => {
                        console.log('✅ [SYNC] Playback synced successfully');
                        ignoreLocalEvents = false;
                        isSyncing = false;
                        ToastNotification.success(`Synced to: ${syncMsg.songName}`);
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
        }
    };

    // Wait for metadata to load before syncing
    if (audioPlayer.readyState >= 2) {
        // Metadata already loaded
        syncAudio();
    } else {
        // Wait for metadata
        const metadataHandler = () => {
            syncAudio();
            audioPlayer.removeEventListener('loadedmetadata', metadataHandler);
        };

        audioPlayer.addEventListener('loadedmetadata', metadataHandler, {once: true});

        // Timeout fallback
        setTimeout(() => {
            audioPlayer.removeEventListener('loadedmetadata', metadataHandler);
            if (isSyncing) {
                console.warn('⚠️ [SYNC] Metadata timeout - attempting sync anyway');
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

    if (isSyncing) {
        console.log('⏭️ [SYNC] Already syncing - skipping duplicate request');
        return;
    }

    console.log('🔄 [SYNC] Requesting playback state...');
    isSyncing = true;

    const syncRequest = {
        username: currentUsername,
        timestamp: Date.now()
    };

    try {
        stompClient.send(
            `/app/music/chat/${currentRoomName}/playback/sync`,
            {},
            JSON.stringify(syncRequest)
        );

        // Set timeout in case no response
        syncTimeout = setTimeout(() => {
            if (isSyncing && !hasReceivedInitialSync) {
                console.log('⏱️ [SYNC] No sync response received - assuming no active playback');
                isSyncing = false;
            }
        }, 3000);

    } catch (error) {
        console.error('❌ [SYNC] Error requesting sync:', error);
        isSyncing = false;
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

    try {
        const messageJson = JSON.stringify(playbackMessage);
        stompClient.send(
            `/app/music/chat/${currentRoomName}/playback`,
            {},
            messageJson
        );
    } catch (error) {
        console.error('❌ Error sending playback message:', error);
        ToastNotification.error('Failed to play song');
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

    // Priority 1: Favorites playlist
    if (playlistMode === 'favorites' && roomFavorites.length > 0) {
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
    }

    // Priority 2: Search results with fallback
    else if (playlistMode === 'search' && searchSongsList.length > 0) {
        if (currentPlaylistIndex >= searchSongsList.length - 1) {
            // End of search - try fallback
            const currentSong = searchSongsList[currentPlaylistIndex];

            // Check if song is in favorites
            const favIndex = roomFavorites.findIndex(s => s.fileName === currentSong.fileName);
            if (favIndex !== -1 && favIndex < roomFavorites.length - 1) {
                // Switch to favorites
                playlistMode = 'favorites';
                currentPlaylistIndex = favIndex;
                currentFavoriteIndex = favIndex;
                ToastNotification.info('🔄 Continuing from favorites');
                playNextSong(); // Recursive with new mode
                return;
            }

            // Fallback to main playlist (current page)
            if (allSongsOnPage.length > 0) {
                playlistMode = 'all';
                currentPlaylistIndex = 0;
                nextSong = allSongsOnPage[0];
                ToastNotification.info('🔄 Continuing from main playlist');
            } else {
                ToastNotification.info('🎵 End of search results');
                return;
            }
        } else {
            nextIndex = currentPlaylistIndex + 1;
            nextSong = searchSongsList[nextIndex];
            currentPlaylistIndex = nextIndex;
        }
    }

    // Priority 3: Main playlist with pagination
    else if (playlistMode === 'all' && allSongsOnPage.length > 0) {
        if (currentPlaylistIndex >= allSongsOnPage.length - 1) {
            // At end of page - check for next page
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
    }

    else {
        ToastNotification.warning('No playlist available to skip');
        return;
    }

    if (nextSong) {
        ToastNotification.info(`⏭️ Next: ${nextSong.songName}`);
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

    // Priority 1: Favorites
    if (playlistMode === 'favorites' && roomFavorites.length > 0) {
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
    }

    // Priority 2: Search results
    else if (playlistMode === 'search' && searchSongsList.length > 0) {
        if (currentPlaylistIndex === 0) {
            ToastNotification.info('🎵 Currently at first song');
            return;
        }

        prevIndex = currentPlaylistIndex - 1;
        prevSong = searchSongsList[prevIndex];
        currentPlaylistIndex = prevIndex;
    }

    // Priority 3: Main playlist with pagination
    else if (playlistMode === 'all' && allSongsOnPage.length > 0) {
        if (currentPlaylistIndex === 0) {
            // At start of page - check for previous page
            if (currentPage > 0) {
                loadPreviousPageAndPlay();
                return;
            } else {
                ToastNotification.info('🎵 Currently at first song');
                return;
            }
        }

        prevIndex = currentPlaylistIndex - 1;
        prevSong = allSongsOnPage[prevIndex];
        currentPlaylistIndex = prevIndex;
    }

    else {
        ToastNotification.warning('No playlist available to skip');
        return;
    }

    if (prevSong) {
        ToastNotification.info(`⏮️ Previous: ${prevSong.songName}`);
        broadcastSkipCommand('PREVIOUS', prevSong, prevIndex);
    }
}

function broadcastSkipCommand(action, song = null, index = 0) {
    if (!stompClient || !stompClient.connected) {
        console.error('Cannot broadcast skip - WebSocket not connected');
        return;
    }

    const skipMessage = {
        action: action,
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
        ToastNotification.info('🎵 No more pages available');
        return;
    }

    ToastNotification.info('Loading next page...');
    await loadSongsForPage(currentPage + 1);

    // Play first song of new page
    if (allSongsOnPage.length > 0) {
        currentPlaylistIndex = 0;
        playlistMode = 'all';
        const firstSong = allSongsOnPage[0];
        ToastNotification.success(`Next page loaded: ${firstSong.songName}`);
        playSong(firstSong);
    }
}

async function loadPreviousPageAndPlay() {
    if (currentPage === 0) {
        ToastNotification.info('🎵 Already on first page');
        return;
    }

    ToastNotification.info('Loading previous page...');
    await loadSongsForPage(currentPage - 1);

    // Play last song of new page
    if (allSongsOnPage.length > 0) {
        currentPlaylistIndex = allSongsOnPage.length - 1;
        playlistMode = 'all';
        const lastSong = allSongsOnPage[currentPlaylistIndex];
        ToastNotification.success(`Previous page loaded: ${lastSong.songName}`);
        playSong(lastSong);
    }
}
// ==================== WEBSOCKET CONNECTION ====================
function connectWebSocket(token) {
    const socket = new SockJS('/app/music/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect(
        {'Authorization': `Bearer ${token}`},
        (frame) => {
            console.log('✅ Connected to WebSocket');
            ToastNotification.success('Connected to chat room');

            // Chat messages
            stompClient.subscribe(`/topic/chat/${currentRoomName}`, (message) => {
                const msg = JSON.parse(message.body);

                if (msg.type === "LEAVE") {
                    lastUserLeft = msg.sender;
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
                handlePlaybackCommand(playbackMsg);
            });

            // NEW: Subscribe to playback sync state responses
            stompClient.subscribe(`/topic/chat/${currentRoomName}/playback/state`, (message) => {
                const syncMsg = JSON.parse(message.body);
                handlePlaybackSyncState(syncMsg);
            });

            // Typing events
            stompClient.subscribe(`/topic/chat/${currentRoomName}/typing`, (message) => {
                const typingData = JSON.parse(message.body);
                handleTypingIndicator(typingData);
            });

            // Skip commands
            stompClient.subscribe(`/topic/chat/${currentRoomName}/skip`, (message) => {
                const skipMsg = JSON.parse(message.body);
                handleSkipCommand(skipMsg);
            });

            // Participants updates
            stompClient.subscribe(`/topic/chat/${currentRoomName}/participants`, (message) => {
                const participants = JSON.parse(message.body);
                updateParticipantsDisplay(participants);
            });

            // Shared room favorites
            stompClient.subscribe(`/topic/chat/${currentRoomName}/favorites`, (message) => {
                const favoritesMsg = JSON.parse(message.body);
                handleFavoritesUpdate(favoritesMsg);
            });

            // Send JOIN message
            stompClient.send(`/app/music/chat/${currentRoomName}/addUser`, {}, JSON.stringify({
                sender: currentUsername,
                type: 'JOIN',
                content: `${currentUsername} joined the room`
            }));

            // Request current favorites state
            requestFavoritesSync();

            // NEW: Request playback sync for late joiners
            setTimeout(() => {
                requestPlaybackSync();
            }, 500);

            startParticipantRefreshInterval();
        },
        (error) => {
            console.error('❌ WebSocket error:', error);
            ToastNotification.error('Connection error. Reconnecting...');
            setTimeout(() => {
                if (stompClient && !stompClient.connected) {
                    connectWebSocket(token);
                }
            }, 3000);
        }
    );
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
        stompClient.send(
            `/app/music/chat/${currentRoomName}/favorites/sync`,
            {},
            JSON.stringify({username: currentUsername})
        );
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

        if (p.userName === currentUsername) {
            const wasOrganizer = isOrganizer;
            isOrganizer = p.organizer;

            if (wasOrganizer !== isOrganizer) {
                console.log('🎭 User role changed from', wasOrganizer ? 'ORGANIZER' : 'PARTICIPANT',
                    'to', isOrganizer ? 'ORGANIZER' : 'PARTICIPANT');
                onRoleChange();

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

    console.log('💖 Toggling favorite for:', song.songName);
    toggleFavorite(song, heartButton);
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

    const favIndex = roomFavorites.findIndex(s => s.fileName === song.fileName);
    if (favIndex !== -1) {
        playlistMode = 'favorites';
        currentPlaylist = [...roomFavorites];
        currentPlaylistIndex = favIndex;
        currentFavoriteIndex = favIndex;
    } else {
        playlistMode = 'all';
        currentPlaylist = [...allSongsOnPage];
        currentPlaylistIndex = allSongsOnPage.findIndex(s => s.fileName === song.fileName);

        if (currentPlaylistIndex === -1) {
            currentPlaylist.push(song);
            currentPlaylistIndex = currentPlaylist.length - 1;
        }
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

    if (isPlayingFavorites) {
        stopFavoritesPlaylist();
    }

    const favIndex = roomFavorites.findIndex(s => s.fileName === song.fileName);
    if (favIndex !== -1) {
        playlistMode = 'favorites';
        currentPlaylist = [...roomFavorites];
        currentPlaylistIndex = favIndex;
        currentFavoriteIndex = favIndex;
    } else {
        playlistMode = 'search';
        currentPlaylist = [...searchSongsList];
        currentPlaylistIndex = searchSongsList.findIndex(s => s.fileName === song.fileName);

        if (currentPlaylistIndex === -1) {
            currentPlaylist.push(song);
            currentPlaylistIndex = currentPlaylist.length - 1;
        }
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

function handleTabClose() {
    if (isReload()) {
        return;
    }

    if (isClosing) {
        return;
    }
    isClosing = true;

    if (participantRefreshInterval) {
        clearInterval(participantRefreshInterval);
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
        } catch (_) {}
    }
}

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
        await sendLeaveOverWebSocket();
    } catch (err) {
        console.error("Logout error:", err);
    } finally {
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

            stompClient.disconnect(() => {
                resolve();
            });

        } catch (e) {
            console.error("WS disconnect error:", e);
            resolve();
        }
    });
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