// ==================== GLOBAL VARIABLES ====================
let stompClient = null;
let currentPage = 0;
let isLoadingSongs = false;
let hasMoreSongs = true;
let currentRoomName = null;
let currentUsername = null;
let currentUserColor = null;
let currentUserDarkerColor = null;
let syncInterval = null;
let participantRefreshInterval = null;
let isOrganizer = false;
let ignoreLocalEvents = false;
let jwtToken = null;
let audioLoadTimeout = null;
let currentSongData = null;
const userColors = {};
const colors = ['#1a1a1a', '#2d2d2d', '#3d3d3d', '#505050', '#636363', '#767676'];

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

    console.log('✅ Initialized chat for:', currentUsername, 'in room:', currentRoomName);
    console.log('✅ Is organizer:', isOrganizer);

    updatePermissionNotice();
    setupAudioPlayerListeners();
    connectWebSocket(jwtToken);

    const songList = document.getElementById('songList');
    songList.addEventListener('scroll', () => {
        if (songList.scrollTop + songList.clientHeight >= songList.scrollHeight - 50) {
            loadSongs();
        }
    });

}


// ==================== UPDATE CURRENT SONG DISPLAY ====================
function updateCurrentSongDisplay(songName, hero, heroine, language) {
    document.getElementById('currentSongTitle').textContent = songName;
    document.getElementById('currentSongDetails').textContent = `${hero} • ${heroine} • ${language}`;
}

// ==================== AUDIO PLAYER CONTROL ====================
let lastPlaybackAction = null;

function setupAudioPlayerListeners() {
    const audioPlayer = document.getElementById('audioPlayer');

    if (!audioPlayer) {
        console.error('❌ Audio player not found!');
        return;
    }

    console.log('🎵 Setting up audio player listeners for:', isOrganizer ? 'ORGANIZER' : 'NON-ORGANIZER');

    // Only organizer can send pause events
    audioPlayer.addEventListener('pause', () => {
        if (!ignoreLocalEvents && isOrganizer && stompClient && stompClient.connected && audioPlayer.src) {
            lastPlaybackAction = 'PAUSE';
            const playbackMessage = {
                action: 'PAUSE',
                timestamp: Math.floor(audioPlayer.currentTime * 1000),
                controller: currentUsername
            };
            console.log('⏸️ [ORGANIZER] Sending PAUSE command:', playbackMessage);
            stompClient.send(`/app/music/chat/${currentRoomName}/playback`, {}, JSON.stringify(playbackMessage));
        }
    });

    // Only organizer can send play events
    audioPlayer.addEventListener('play', () => {
        if (!ignoreLocalEvents && isOrganizer && stompClient && stompClient.connected && audioPlayer.src) {
            if (lastPlaybackAction === 'PLAY') {
                lastPlaybackAction = null;
                return;
            }

            lastPlaybackAction = 'RESUME';
            const playbackMessage = {
                action: 'RESUME',
                timestamp: Math.floor(audioPlayer.currentTime * 1000),
                controller: currentUsername
            };
            console.log('▶️ [ORGANIZER] Sending RESUME command:', playbackMessage);
            stompClient.send(`/app/music/chat/${currentRoomName}/playback`, {}, JSON.stringify(playbackMessage));
        }
    });

    // Only organizer can send seek events
    audioPlayer.addEventListener('seeked', () => {
        if (!ignoreLocalEvents && isOrganizer && stompClient && stompClient.connected && audioPlayer.src) {
            const playbackMessage = {
                action: 'SEEK',
                timestamp: Math.floor(audioPlayer.currentTime * 1000),
                controller: currentUsername
            };
            console.log('🔤 [ORGANIZER] Sending SEEK command:', playbackMessage);
            stompClient.send(`/app/music/chat/${currentRoomName}/playback`, {}, JSON.stringify(playbackMessage));
        }
    });

    // Prevent non-organizer from playing audio manually
    if (!isOrganizer) {
        console.log('🔒 [NON-ORGANIZER] Locking audio player...');

        // Prevent manual play
        audioPlayer.addEventListener('play', (e) => {
            if (!ignoreLocalEvents) {
                console.log('🔒 [NON-ORGANIZER] Prevented manual play attempt');
                audioPlayer.pause();
            }
        }, true);

        // Prevent manual pause
        audioPlayer.addEventListener('pause', (e) => {
            if (!ignoreLocalEvents && !audioPlayer.paused) {
                console.log('🔒 [NON-ORGANIZER] Prevented manual pause attempt');
                audioPlayer.play().catch(err => console.log('Resume from prevented pause:', err));
            }
        }, true);

        // Prevent manual seeking
        audioPlayer.addEventListener('seeking', (e) => {
            if (!ignoreLocalEvents) {
                console.log('🔒 [NON-ORGANIZER] Prevented seek attempt');
                e.preventDefault();
            }
        }, true);
    }
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
        showSystemMessage('Error: ' + playbackMsg.content);
        ignoreLocalEvents = false;
        return;
    }

    console.log('📥 [' + currentUsername + '] Received playback command:', playbackMsg.action, 'from:', playbackMsg.controller);
    console.log('   Current Audio State - Src:', audioPlayer.src ? 'SET' : 'EMPTY', 'Playing:', !audioPlayer.paused, 'Time:', audioPlayer.currentTime.toFixed(2) + 's');

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

        case 'STOP':
            handleStopCommand(audioPlayer, playbackMsg);
            break;

        case 'SEEK':
            handleSeekCommand(audioPlayer, playbackMsg);
            break;

        case 'SYNC':
            handleSyncCommand(audioPlayer, playbackMsg);
            break;

        default:
            console.warn('⚠️ Unknown playback action:', playbackMsg.action);
            ignoreLocalEvents = false;
    }
}

function handlePlayCommand(audioPlayer, playbackMsg) {
    const newSrc = `/app/music/public/streamSong/${playbackMsg.songFileName}`;
    currentSongData = {
        songName: playbackMsg.songName,
        hero: playbackMsg.hero,
        heroine: playbackMsg.heroine,
        language: playbackMsg.language
    };

    console.log('🎵 [' + currentUsername + '] Playing song:', playbackMsg.songName, 'at time:', playbackMsg.timestamp + 'ms');
    updateCurrentSongDisplay(playbackMsg.songName, playbackMsg.hero, playbackMsg.heroine, playbackMsg.language);

    const sourceChanged = audioPlayer.src !== newSrc;
    console.log('   Source changed:', sourceChanged, '| Organizer:', isOrganizer);

    if (sourceChanged) {
        audioPlayer.src = newSrc;
    }

    const startTime = playbackMsg.timestamp ? playbackMsg.timestamp / 1000 : 0;
    console.log('   Setting startTime to:', startTime.toFixed(2), 'seconds');

    if (sourceChanged) {
        // New source - wait for metadata
        const metadataHandler = () => {
            console.log('   ✅ Metadata loaded, seeking to', startTime.toFixed(2), 'and playing');
            audioPlayer.currentTime = startTime;

            const playPromise = audioPlayer.play();
            if (playPromise !== undefined) {
                playPromise.catch(err => {
                    console.error('   ❌ Play error:', err.message);
                    ignoreLocalEvents = false;
                });
            }

            audioPlayer.removeEventListener('loadedmetadata', metadataHandler);
            if (audioLoadTimeout) clearTimeout(audioLoadTimeout);
            setTimeout(() => {
                ignoreLocalEvents = false;
            }, 200);
        };

        audioPlayer.addEventListener('loadedmetadata', metadataHandler, {once: true});

        // Timeout if metadata doesn't load
        audioLoadTimeout = setTimeout(() => {
            console.warn('   ⚠️ Audio metadata loading timeout, attempting to play anyway');
            audioPlayer.removeEventListener('loadedmetadata', metadataHandler);
            audioPlayer.currentTime = startTime;

            const playPromise = audioPlayer.play();
            if (playPromise !== undefined) {
                playPromise.catch(err => {
                    console.error('   ❌ Play error after timeout:', err.message);
                    ignoreLocalEvents = false;
                });
            }
            setTimeout(() => {
                ignoreLocalEvents = false;
            }, 200);
        }, 3000);
    } else {
        // Same source, just seek and play
        console.log('   Same source, seeking and playing');
        audioPlayer.currentTime = startTime;

        const playPromise = audioPlayer.play();
        if (playPromise !== undefined) {
            playPromise.catch(err => {
                console.error('   ❌ Play error:', err.message);
                ignoreLocalEvents = false;
            });
        }
        setTimeout(() => {
            ignoreLocalEvents = false;
        }, 200);
    }
}

function handlePauseCommand(audioPlayer, playbackMsg) {
    console.log('⏸️ Pausing at timestamp:', (playbackMsg.timestamp / 1000).toFixed(2), 'seconds');
    const pauseTime = playbackMsg.timestamp / 1000;
    audioPlayer.currentTime = pauseTime;
    audioPlayer.pause();
    showSystemMessage(`${playbackMsg.controller} paused the music`);
    setTimeout(() => {
        ignoreLocalEvents = false;
    }, 100);
}

function handleResumeCommand(audioPlayer, playbackMsg) {
    console.log('▶️ Resuming from timestamp:', (playbackMsg.timestamp / 1000).toFixed(2), 'seconds');
    if (playbackMsg.timestamp !== undefined) {
        audioPlayer.currentTime = playbackMsg.timestamp / 1000;
    }

    const playPromise = audioPlayer.play();
    if (playPromise !== undefined) {
        playPromise.catch(err => {
            console.error('Resume error:', err.message);
            ignoreLocalEvents = false;
        });
    }
    showSystemMessage(`${playbackMsg.controller} resumed the music`);
    setTimeout(() => {
        ignoreLocalEvents = false;
    }, 100);
}

function handleStopCommand(audioPlayer, playbackMsg) {
    console.log('⏹️ Stopping playback');
    audioPlayer.pause();
    audioPlayer.currentTime = 0;
    showSystemMessage(`${playbackMsg.controller} stopped the music`);
    setTimeout(() => {
        ignoreLocalEvents = false;
    }, 100);
}

function handleSeekCommand(audioPlayer, playbackMsg) {
    console.log('🔍 Seeking to:', (playbackMsg.timestamp / 1000).toFixed(2), 'seconds');
    audioPlayer.currentTime = playbackMsg.timestamp / 1000;
    showSystemMessage(`${playbackMsg.controller} seeked to ${Math.floor(playbackMsg.timestamp / 1000)}s`);
    setTimeout(() => {
        ignoreLocalEvents = false;
    }, 100);
}

function handleSyncCommand(audioPlayer, playbackMsg) {
    if (audioPlayer.src && !audioPlayer.paused) {
        const currentTime = audioPlayer.currentTime;
        const targetTime = playbackMsg.timestamp / 1000;
        const drift = Math.abs(currentTime - targetTime);

        // Only sync if drift is significant (> 3 seconds) and NOT from same user
        if (drift > 3 && playbackMsg.controller !== currentUsername) {
            console.log(`🔄 [${currentUsername}] Syncing playback from ${playbackMsg.controller}, drift: ${drift.toFixed(2)}s`);
            ignoreLocalEvents = true;
            audioPlayer.currentTime = targetTime;
            setTimeout(() => {
                ignoreLocalEvents = false;
            }, 200);
        } else {
            console.log(`⏭️ [${currentUsername}] Ignoring SYNC - drift: ${drift.toFixed(2)}s, controller: ${playbackMsg.controller}`);
            setTimeout(() => {
                ignoreLocalEvents = false;
            }, 100);
        }
    } else {
        setTimeout(() => {
            ignoreLocalEvents = false;
        }, 100);
    }
}

function playSong(song) {
    if (!isOrganizer) {
        showSystemMessage('Only the organizer can play songs');
        return;
    }

    if (stompClient && stompClient.connected) {
        lastPlaybackAction = 'PLAY';
        const playbackMessage = {
            action: 'PLAY',
            songFileName: song.fileName,
            songName: song.songName,
            hero: song.hero,
            heroine: song.heroine,
            language: song.language,
            controller: currentUsername,
            timestamp: 0
        };
        console.log('🎵 [ORGANIZER] Playing song:', song.songName);
        stompClient.send(`/app/music/chat/${currentRoomName}/playback`, {}, JSON.stringify(playbackMessage));
    } else {
        console.error('❌ WebSocket not connected');
        showSystemMessage('Error: WebSocket not connected. Please refresh.');
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

            stompClient.subscribe(`/topic/chat/${currentRoomName}`, (message) => {
                const chatMessage = JSON.parse(message.body);
                displayMessage(chatMessage);
            });

            stompClient.subscribe(`/topic/chat/${currentRoomName}/playback`, (message) => {
                const playbackMsg = JSON.parse(message.body);
                handlePlaybackCommand(playbackMsg);
            });

            stompClient.subscribe(`/topic/chat/${currentRoomName}/participants`, (message) => {
                const participants = JSON.parse(message.body);
                updateParticipantsDisplay(participants);
            });

            stompClient.send(`/app/music/chat/${currentRoomName}/addUser`, {}, JSON.stringify({
                sender: currentUsername,
                type: 'JOIN',
                content: `${currentUsername} joined the room`
            }));

            startSyncInterval();
            startParticipantRefreshInterval();
        },
        (error) => {
            console.error('❌ WebSocket error:', error);
            setTimeout(() => {
                if (stompClient && !stompClient.connected) {
                    console.log('🔄 Attempting to reconnect...');
                    connectWebSocket(token);
                }
            }, 3000);
        }
    );
}

function startSyncInterval() {
    if (syncInterval) {
        clearInterval(syncInterval);
    }

    syncInterval = setInterval(() => {
        const audioPlayer = document.getElementById('audioPlayer');
        if (!audioPlayer.paused && audioPlayer.src && stompClient && stompClient.connected && isOrganizer) {
            const syncMessage = {
                action: 'SYNC',
                timestamp: Math.floor(audioPlayer.currentTime * 1000),
                controller: currentUsername
            };
            stompClient.send(`/app/music/chat/${currentRoomName}/playback`, {}, JSON.stringify(syncMessage));
        }
    }, 10000);
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

    participantsList.innerHTML = '';
    participantCount.textContent = `${participants.length} / ${PAGE_DATA.totalCount} participants`;

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
                <div class="participant-name" style="background: linear-gradient(135deg, ${userColor}, ${darkerColor})">${p.userName}</div>
                ${p.organizer ? '<span class="organizer-badge">Organizer</span>' : ''}
            `;
        participantsList.appendChild(item);

        if (p.userName === currentUsername) {
            isOrganizer = p.organizer;
            updatePermissionNotice();
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
        showSystemMessage('Error: Not connected to chat. Please refresh the page.');
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
        console.log('✅ Message sent:', content);
    } catch (error) {
        console.error('❌ Error sending message:', error);
        showSystemMessage('Error sending message. Please try again.');
    }
}

function displayMessage(message) {
    const chatMessages = document.getElementById('chatMessages');

    if (message.type === 'JOIN' || message.type === 'LEAVE') {
        const systemMsg = document.createElement('div');
        systemMsg.className = 'system-message';
        systemMsg.textContent = message.content;
        chatMessages.appendChild(systemMsg);

        setTimeout(() => {
            systemMsg.classList.add('fade-out');
            setTimeout(() => {
                if (systemMsg.parentNode) {
                    systemMsg.parentNode.removeChild(systemMsg);
                }
            }, 500);
        }, 10000);
    } else {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message';

        const time = new Date().toLocaleTimeString('en-US', {hour: '2-digit', minute: '2-digit'});

        let userColor, darkerColor;
        if (message.sender === currentUsername) {
            userColor = currentUserColor;
            darkerColor = currentUserDarkerColor;
        } else {
            userColor = getUserColor(message.sender);
            darkerColor = getDarkerShade(userColor);
        }

        messageDiv.innerHTML = `
                <div class="message-header">
                    <span class="message-sender" style="background: linear-gradient(135deg, ${userColor}, ${darkerColor})">${message.sender}</span>
                    <span class="message-time">${time}</span>
                </div>
                <div class="message-content">${escapeHtml(message.content)}</div>
            `;

        chatMessages.appendChild(messageDiv);
    }

    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function showSystemMessage(text) {
    const chatMessages = document.getElementById('chatMessages');
    const systemMsg = document.createElement('div');
    systemMsg.className = 'system-message';
    systemMsg.textContent = text;
    chatMessages.appendChild(systemMsg);

    setTimeout(() => {
        systemMsg.classList.add('fade-out');
        setTimeout(() => {
            if (systemMsg.parentNode) {
                systemMsg.parentNode.removeChild(systemMsg);
            }
        }, 500);
    }, 5000);

    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// ==================== SONG MANAGEMENT ====================
async function loadSongs() {
    if (isLoadingSongs || !hasMoreSongs) return;

    isLoadingSongs = true;

    try {
        const response = await fetch(`/app/music/fetchAllSongs?page=${currentPage}&size=10`, {
            headers: {
                'Authorization': `Bearer ${jwtToken}`
            }
        });

        if (response.ok) {
            const data = await response.json();
            displaySongs(data.content);
            hasMoreSongs = !data.last;
            currentPage++;
        }
    } catch (error) {
        console.error('Error loading songs:', error);
    } finally {
        isLoadingSongs = false;
    }
}

function displaySongs(songs) {
    const songList = document.getElementById('songList');
    if (currentPage === 1) {
        songList.innerHTML = '';
    }

    songs.forEach(song => {
        const songItem = document.createElement('div');
        songItem.className = 'song-item';
        songItem.dataset.filename = song.fileName;
        songItem.dataset.songname = song.songName;
        songItem.dataset.hero = song.hero;
        songItem.dataset.heroine = song.heroine;
        songItem.dataset.language = song.language;
        songItem.onclick = () => handleSongClick(songItem);
        songItem.innerHTML = `
                <div class="song-item-title">${song.songName}</div>
                <div class="song-item-info">${song.hero || 'Unknown'} • ${song.heroine || 'Unknown'} • ${song.language || 'Unknown'}</div>
            `;
        songList.appendChild(songItem);
    });
}

function handleSongClick(element) {
    if (!isOrganizer) {
        showSystemMessage('Only the organizer can play songs');
        return;
    }

    const song = {
        fileName: element.dataset.filename,
        songName: element.dataset.songname,
        hero: element.dataset.hero,
        heroine: element.dataset.heroine,
        language: element.dataset.language
    };
    playSong(song);
}

async function searchSongs() {
    const query = document.getElementById('searchInput').value.trim();
    if (!query) return;

    try {
        const response = await fetch(`/app/music/searchSong?query=${encodeURIComponent(query)}`, {
            headers: {
                'Authorization': `Bearer ${jwtToken}`
            }
        });

        if (response.ok) {
            const songs = await response.json();
            displaySearchResults(songs);
        }
    } catch (error) {
        console.error('Error searching songs:', error);
    }
}

function displaySearchResults(songs) {
    const searchResults = document.getElementById('searchResults');
    searchResults.innerHTML = '';

    if (songs.length === 0) {
        searchResults.innerHTML = '<div class="loading">No songs found</div>';
        return;
    }

    songs.forEach(song => {
        const songItem = document.createElement('div');
        songItem.className = 'song-item';
        songItem.dataset.filename = song.fileName;
        songItem.dataset.songname = song.songName;
        songItem.dataset.hero = song.hero;
        songItem.dataset.heroine = song.heroine;
        songItem.dataset.language = song.language;
        songItem.onclick = () => {
            if (isOrganizer) {
                handleSongClick(songItem);
                closeSearchDrawer();
            } else {
                showSystemMessage('Only the organizer can play songs');
            }
        };
        songItem.innerHTML = `
                <div class="song-item-title">${song.songName}</div>
                <div class="song-item-info">${song.hero || 'Unknown'} • ${song.heroine || 'Unknown'} • ${song.language || 'Unknown'}</div>
            `;
        searchResults.appendChild(songItem);
    });
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

// ==================== LOGOUT ====================
async function logout() {
    if (syncInterval) {
        clearInterval(syncInterval);
    }

    if (participantRefreshInterval) {
        clearInterval(participantRefreshInterval);
    }

    if (stompClient && stompClient.connected) {
        stompClient.send(`/app/music/chat/${currentRoomName}/removeUser`, {}, JSON.stringify({
            sender: currentUsername,
            type: 'LEAVE',
            content: `${currentUsername} left the room`
        }));
        stompClient.disconnect();
    }

    try {
        await fetch(`/app/music/room/leave?roomName=${encodeURIComponent(currentRoomName)}&username=${encodeURIComponent(currentUsername)}`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${jwtToken}`
            }
        });
    } catch (error) {
        console.error('Error leaving room:', error);
    }

    localStorage.removeItem('jwtToken');
    localStorage.removeItem('username');
    localStorage.removeItem('currentRoom');
    document.cookie = 'jwtToken=; path=/; max-age=0; SameSite=Lax';
    window.location.href = '/app/music/public/login';
}

window.addEventListener('beforeunload', () => {
    if (syncInterval) {
        clearInterval(syncInterval);
    }

    if (participantRefreshInterval) {
        clearInterval(participantRefreshInterval);
    }

    if (stompClient && stompClient.connected) {
        stompClient.send(`/app/music/chat/${currentRoomName}/removeUser`, {}, JSON.stringify({
            sender: currentUsername,
            type: 'LEAVE',
            content: `${currentUsername} left the room`
        }));
    }

    // Handle success message
    const successMessageContainer = document.getElementById('successMessage');
    const successMessage = successMessageContainer.textContent.trim();
    if (successMessage === 'true') {
        notifier.success('Room created successfully! Redirecting to chat...', 0);
    }
});
