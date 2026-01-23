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
}

// Initialize notifier
const notifier = new ToastNotifier();

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

// Handle error and success messages on page load
window.addEventListener('DOMContentLoaded', function() {
    // Initialize song autocomplete
    new SongAutocomplete();

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

    // Handle field validation errors for Create Room
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

    // Handle field validation errors for Confess Form
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

    // Handle request song field errors
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

    // Handle request song error/success flash attribute - FIXED
    const requestSongErrorContainer = document.getElementById('requestSongError');
    if (requestSongErrorContainer) {
        const requestSongError = requestSongErrorContainer.textContent.trim();
        if (requestSongError) {
            // Check if it's a success message
            if (requestSongError.toLowerCase().includes('success') ||
                requestSongError.toLowerCase().includes('submitted') ||
                requestSongError.toLowerCase().includes('received')) {
                notifier.success(requestSongError);  // ✅ GREEN notification
                hasRequestSongSuccess = true;
                clearRequestSongForm();  // Clear form on success
            } else {
                notifier.error(requestSongError);  // ❌ RED notification
                hasRequestSongErrors = true;
            }
        }
    }

    // Handle creation error
    const creationErrorContainer = document.getElementById('creationError');
    if (creationErrorContainer) {
        const creationError = creationErrorContainer.textContent.trim();
        if (creationError) {
            notifier.error(creationError);
            createModal.show();
            hasErrors = true;
        }
    }

    // Handle join error
    const joinErrorContainer = document.getElementById('joinError');
    if (joinErrorContainer) {
        const joinError = joinErrorContainer.textContent.trim();
        if (joinError) {
            notifier.error(joinError);
            joinModal.show();
            hasErrors = true;
        }
    }

    // Handle email status from Send Confess form - FIXED
    const emailStatusElement = document.getElementById('emailStatus');
    if (emailStatusElement) {
        const emailStatus = emailStatusElement.textContent.trim();
        if (emailStatus) {
            // Check if it's a success message
            if (emailStatus.toLowerCase().includes('success') ||
                emailStatus.toLowerCase().includes('sent') ||
                emailStatus.toLowerCase().includes('submitted')) {
                notifier.success(emailStatus);  // ✅ GREEN notification
                hasConfessSuccess = true;
                clearConfessForm();  // Clear form on success
            } else {
                notifier.error(emailStatus);  // ❌ RED notification
                hasConfessErrors = true;
            }
        }
    }

    // Open the appropriate modal if there are errors (but not on success)
    if (hasConfessErrors && !hasConfessSuccess) {
        confessModal.show();
    }

    if (hasRequestSongErrors && !hasRequestSongSuccess) {
        requestSongModal.show();
    }

    // Clear fields ONLY if there are actual errors for Create/Join Room
    if (hasErrors) {
        const roomNameField = document.getElementById('roomName');
        const maxCountField = document.getElementById('maxCount');
        const joinRoomNameField = document.getElementById('joinRoomName');

        if (roomNameField) roomNameField.value = '';
        if (maxCountField) maxCountField.value = '';
        if (joinRoomNameField) joinRoomNameField.value = '';
    }

    // Initialize Bootstrap tooltips
    const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl);
    });
});

document.addEventListener('DOMContentLoaded', function() {
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
});

function dashboardLogout() {
    window.location.href = '/app/music/public/logout';
}