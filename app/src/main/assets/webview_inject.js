(function() {
    // 创建样式元素
    const styleElement = document.createElement('style');
    styleElement.textContent = `
        #custom-controls {
            display: none !important;
            position: fixed;
            bottom: 0;
            left: 0;
            right: 0;
            background: rgba(0, 0, 0, 0.8);
            backdrop-filter: blur(2px);
            transition: all 0.3s ease;
            opacity: 0;
            transform: translateY(100%);
            z-index: 2147483647;
            padding: 10px;
        }
        #custom-controls.visible {
            opacity: 1;
            transform: translateY(0);
        }
        #progress-container {
            position: relative;
            height: 4px;
            background: rgba(255, 255, 255, 0.2);
            border-radius: 2px;
            cursor: pointer;
            margin-bottom: 10px;
        }
        #progress-bar {
            position: absolute;
            top: 0;
            left: 0;
            height: 100%;
            background: #3B82F6;
            border-radius: 2px;
            transition: width 0.1s linear;
        }
        #progress-buffer {
            position: absolute;
            top: 0;
            left: 0;
            height: 100%;
            background: rgba(59, 130, 246, 0.3);
            border-radius: 2px;
            transition: width 0.1s linear;
        }
        #progress-handle {
            position: absolute;
            top: 50%;
            left: 0;
            transform: translate(-50%, -50%) scale(0);
            width: 12px;
            height: 12px;
            background: white;
            border-radius: 50%;
            box-shadow: 0 0 5px rgba(0, 0, 0, 0.3);
            transition: transform 0.1s ease;
        }
        #progress-container:hover #progress-handle {
            transform: translate(-50%, -50%) scale(1);
        }
        .control-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            color: white;
        }
        .control-left, .control-right {
            display: flex;
            align-items: center;
        }
        .control-btn {
            background: none;
            border: none;
            color: white;
            font-size: 18px;
            margin: 0 8px;
            cursor: pointer;
            transition: color 0.2s ease;
        }
        .control-btn:hover {
            color: #3B82F6;
        }
        .time-display {
            font-size: 14px;
            margin: 0 8px;
        }
        .volume-container {
            position: relative;
            display: inline-block;
        }
        .volume-slider {
            position: absolute;
            bottom: 100%;
            left: 0;
            margin-bottom: 10px;
            width: 100px;
            background: rgba(0, 0, 0, 0.8);
            padding: 5px;
            border-radius: 5px;
            display: none;
        }
        .volume-container:hover .volume-slider {
            display: block;
        }
        .aspect-ratio-select {
            background: rgba(0, 0, 0, 0.6);
            border: 1px solid rgba(255, 255, 255, 0.2);
            color: white;
            border-radius: 3px;
            padding: 3px 5px;
            margin: 0 8px;
            font-size: 14px;
        }
    `;
    document.head.appendChild(styleElement);

    const startTime = Date.now();
    let videoElement = null;
    let progressUpdateInterval = null;
    let isSeeking = false;
    let controlTimer = null;
    let controlsVisible = true;
    const HIDE_DELAY = 3000; // 3秒后隐藏控制栏

    // 移除控制条
    function removeControls() {
        const selectors = [
            '#control_bar', '.controls',
            '.vjs-control-bar', 'xg-controls',
            '.xgplayer-ads', '.fixed-layer',
            'div[style*="z-index: 9999"]',
            '.video-controls', '.player-controls', '.live-controls'
        ];

        selectors.forEach((selector) => {
            document.querySelectorAll(selector).forEach((element) => {
                element.style.display = 'none';
                element.parentNode?.removeChild(element);
            });
        });
    }

    // 设置视频比例
    window.setscale = function(scaletype) {
        if (!videoElement) return;

        const container = videoElement.parentElement;
        const baseStyle = `
            position: absolute !important;
            top: 50% !important;
            left: 50% !important;
            transform: translate(-50%, -50%) !important;
            outline: none !important;
            border: none !important;
            box-shadow: none !important;
        `;

        switch (scaletype) {
            case 0: // 默认（21:9）
                videoElement.style.cssText = baseStyle + `
                    width: 100% !important;
                    height: 100% !important;
                    aspect-ratio: 21 / 9 !important;
                `;
                break;

            case 1: // 16:9
                videoElement.style.cssText = baseStyle + `
                    width: 100% !important;
                    height: 100% !important;
                    aspect-ratio: 16 / 9 !important;
                `;
                break;

            case 2: // 4:3
                videoElement.style.cssText = baseStyle + `
                    width: 100% !important;
                    height: 100% !important;
                    aspect-ratio: 4 / 3 !important;
                `;
                break;

            case 3: // 填充
                videoElement.style.cssText = baseStyle + `
                    width: 100% !important;
                    height: 100% !important;
                    object-fit: fill !important;
                `;
                break;

            case 4: // 原始
                videoElement.style.cssText = baseStyle + `
                    width: 100% !important;
                    height: 100% !important;
                    object-fit: none !important;
                `;
                break;

            case 5: // 裁剪
                videoElement.style.cssText = baseStyle + `
                    width: 100% !important;
                    height: 100% !important;
                    object-fit: cover !important;
                `;
                break;
        }
    };

    // 更新进度条
    function updateProgress() {
        if (!videoElement) return;
        
        const progressBar = document.getElementById('progress-bar');
        const progressBuffer = document.getElementById('progress-buffer');
        const progressHandle = document.getElementById('progress-handle');
        const timeDisplay = document.getElementById('time-display');
        
        if (videoElement.buffered.length > 0) {
            const bufferedEnd = videoElement.buffered.end(videoElement.buffered.length - 1);
            const duration = videoElement.duration;
            const bufferedPercent = (bufferedEnd / duration) * 100;
            progressBuffer.style.width = `${bufferedPercent}%`;
        }
        
        if (!isSeeking) {
            const percent = (videoElement.currentTime / videoElement.duration) * 100;
            progressBar.style.width = `${percent}%`;
            progressHandle.style.left = `${percent}%`;
            
            // 更新时间显示
            const currentMinutes = Math.floor(videoElement.currentTime / 60);
            const currentSeconds = Math.floor(videoElement.currentTime % 60);
            const totalMinutes = Math.floor(videoElement.duration / 60);
            const totalSeconds = Math.floor(videoElement.duration % 60);
            
            timeDisplay.textContent = `${currentMinutes}:${currentSeconds.toString().padStart(2, '0')} / ${totalMinutes}:${totalSeconds.toString().padStart(2, '0')}`;
            
            // 更新外部API
            ku9.setposition(videoElement.currentTime);
            ku9.setduration(videoElement.duration);
        }
    }

    // 视频暂停
    function pause() {
        if (videoElement) {
            videoElement.pause();
            document.getElementById('play-pause-btn').innerHTML = '<i class="fa fa-play"></i>';
            showControls(); // 暂停时显示控制栏
        }
    }

    // 视频播放
    function play() {
        if (videoElement) {
            videoElement.play().then(() => {
                document.getElementById('play-pause-btn').innerHTML = '<i class="fa fa-pause"></i>';
                resetHideTimer(); // 开始播放时重置隐藏计时器
            }).catch(error => {
                console.error('播放失败:', error);
            });
        }
    }

    // 拖动视频进度时
    function setposition(position) {
        if (videoElement && !isNaN(position) && position <= videoElement.duration) {
            videoElement.currentTime = position;
            updateProgress();
        }
    }

    // 显示控制栏
    function showControls() {
        const controls = document.getElementById('custom-controls');
        controls.classList.add('visible');
        controlsVisible = true;
        resetHideTimer();
    }

    // 隐藏控制栏
    function hideControls() {
        if (!isSeeking && !videoElement.paused) {
            const controls = document.getElementById('custom-controls');
            controls.classList.remove('visible');
            controlsVisible = false;
        }
    }

    // 重置隐藏计时器
    function resetHideTimer() {
        clearTimeout(controlTimer);
        if (!videoElement.paused) {
            controlTimer = setTimeout(hideControls, HIDE_DELAY);
        }
    }

    // 设置全屏容器
    function setupVideo(video) {
        videoElement = video;
        
        const container = document.createElement('div');
        container.id = 'video-fullscreen-container';
        container.style.cssText = `
            position: fixed !important;
            top: 0 !important;
            left: 0 !important;
            width: 100% !important;
            height: 100% !important;
            z-index: 2147483646 !important;
            background: black !important;
            overflow: hidden !important;
            transform: translateZ(0);
        `;

        // 创建自定义控制栏
        const controls = document.createElement('div');
        controls.id = 'custom-controls';
        
        // 进度条
        const progressContainer = document.createElement('div');
        progressContainer.id = 'progress-container';
        
        const progressBar = document.createElement('div');
        progressBar.id = 'progress-bar';
        
        const progressBuffer = document.createElement('div');
        progressBuffer.id = 'progress-buffer';
        
        const progressHandle = document.createElement('div');
        progressHandle.id = 'progress-handle';
        
        progressContainer.appendChild(progressBuffer);
        progressContainer.appendChild(progressBar);
        progressContainer.appendChild(progressHandle);
        controls.appendChild(progressContainer);
        
        // 控制行
        const controlRow = document.createElement('div');
        controlRow.className = 'control-row';
        
        // 左侧控制
        const controlLeft = document.createElement('div');
        controlLeft.className = 'control-left';
        
        // 播放/暂停按钮
        const playPauseBtn = document.createElement('button');
        playPauseBtn.id = 'play-pause-btn';
        playPauseBtn.className = 'control-btn';
        playPauseBtn.innerHTML = '<i class="fa fa-play"></i>';
        controlLeft.appendChild(playPauseBtn);
        
        // 音量控制
        const volumeContainer = document.createElement('div');
        volumeContainer.className = 'volume-container';
        
        const volumeBtn = document.createElement('button');
        volumeBtn.id = 'volume-btn';
        volumeBtn.className = 'control-btn';
        volumeBtn.innerHTML = '<i class="fa fa-volume-up"></i>';
        
        const volumeSlider = document.createElement('div');
        volumeSlider.className = 'volume-slider';
        
        const volumeInput = document.createElement('input');
        volumeInput.type = 'range';
        volumeInput.min = 0;
        volumeInput.max = 1;
        volumeInput.step = 0.05;
        volumeInput.value = 1;
        volumeInput.className = 'accent-primary';
        volumeInput.id = 'volume-input';
        
        volumeSlider.appendChild(volumeInput);
        volumeContainer.appendChild(volumeBtn);
        volumeContainer.appendChild(volumeSlider);
        controlLeft.appendChild(volumeContainer);
        
        // 画面比例选择
        const aspectRatioSelect = document.createElement('select');
        aspectRatioSelect.id = 'aspect-ratio';
        aspectRatioSelect.className = 'aspect-ratio-select';
        
        const aspectRatios = [
            { value: 0, text: '默认 (21:9)' },
            { value: 1, text: '16:9' },
            { value: 2, text: '4:3' },
            { value: 3, text: '填充' },
            { value: 4, text: '原始' },
            { value: 5, text: '裁剪' }
        ];
        
        aspectRatios.forEach(ar => {
            const option = document.createElement('option');
            option.value = ar.value;
            option.textContent = ar.text;
            aspectRatioSelect.appendChild(option);
        });
        
        controlLeft.appendChild(aspectRatioSelect);
        controlRow.appendChild(controlLeft);
        
        // 右侧控制
        const controlRight = document.createElement('div');
        controlRight.className = 'control-right';
        
        // 时间显示
        const timeDisplay = document.createElement('span');
        timeDisplay.id = 'time-display';
        timeDisplay.className = 'time-display';
        timeDisplay.textContent = '0:00 / 0:00';
        controlRight.appendChild(timeDisplay);
        
        // 全屏按钮
        const fullscreenBtn = document.createElement('button');
        fullscreenBtn.id = 'fullscreen-btn';
        fullscreenBtn.className = 'control-btn';
        fullscreenBtn.innerHTML = '<i class="fa fa-expand"></i>';
        controlRight.appendChild(fullscreenBtn);
        
        controlRow.appendChild(controlRight);
        controls.appendChild(controlRow);
        
        document.body.appendChild(controls);
        
        // 设置画面比例
        aspectRatioSelect.addEventListener('change', () => {
            const scaletype = parseInt(aspectRatioSelect.value);
            setscale(scaletype);
            showControls(); // 操作后显示控制栏
        });
        
        setscale(0); // 默认比例

        document.body.appendChild(container);
        container.appendChild(video);

        // 播放/暂停按钮
        playPauseBtn.addEventListener('click', () => {
            if (videoElement.paused) {
                play();
            } else {
                pause();
            }
            showControls(); // 操作后显示控制栏
        });
        
        // 视频点击播放/暂停
        videoElement.addEventListener('click', () => {
            if (videoElement.paused) {
                play();
            } else {
                pause();
            }
            showControls(); // 操作后显示控制栏
        });

        // 音量控制
        volumeInput.addEventListener('input', () => {
            videoElement.volume = volumeInput.value;
            
            if (volumeInput.value == 0) {
                volumeBtn.innerHTML = '<i class="fa fa-volume-off"></i>';
            } else if (volumeInput.value < 0.5) {
                volumeBtn.innerHTML = '<i class="fa fa-volume-down"></i>';
            } else {
                volumeBtn.innerHTML = '<i class="fa fa-volume-up"></i>';
            }
            showControls(); // 操作后显示控制栏
        });
        
        volumeBtn.addEventListener('click', () => {
            if (videoElement.volume > 0) {
                videoElement.dataset.oldVolume = videoElement.volume;
                videoElement.volume = 0;
                volumeInput.value = 0;
                volumeBtn.innerHTML = '<i class="fa fa-volume-off"></i>';
            } else {
                const oldVolume = videoElement.dataset.oldVolume || 0.8;
                videoElement.volume = oldVolume;
                volumeInput.value = oldVolume;
                
                if (oldVolume < 0.5) {
                    volumeBtn.innerHTML = '<i class="fa fa-volume-down"></i>';
                } else {
                    volumeBtn.innerHTML = '<i class="fa fa-volume-up"></i>';
                }
            }
            showControls(); // 操作后显示控制栏
        });

        // 进度条控制
        progressContainer.addEventListener('mousedown', (e) => {
            isSeeking = true;
            showControls(); // 拖动时显示控制栏
            const rect = progressContainer.getBoundingClientRect();
            const pos = (e.clientX - rect.left) / rect.width;
            setposition(pos * videoElement.duration);
            
            document.addEventListener('mousemove', onMouseMove);
            document.addEventListener('mouseup', onMouseUp);
        });
        
        function onMouseMove(e) {
            const rect = progressContainer.getBoundingClientRect();
            const pos = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
            const position = pos * videoElement.duration;
            
            // 更新进度条显示
            progressBar.style.width = `${pos * 100}%`;
            progressHandle.style.left = `${pos * 100}%`;
            
            // 更新时间显示
            const minutes = Math.floor(position / 60);
            const seconds = Math.floor(position % 60);
            timeDisplay.textContent = `${minutes}:${seconds.toString().padStart(2, '0')} / ${Math.floor(videoElement.duration / 60)}:${Math.floor(videoElement.duration % 60).toString().padStart(2, '0')}`;
        }
        
        function onMouseUp(e) {
            isSeeking = false;
            const rect = progressContainer.getBoundingClientRect();
            const pos = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
            setposition(pos * videoElement.duration);
            resetHideTimer(); // 拖动结束后重置隐藏计时器
            
            document.removeEventListener('mousemove', onMouseMove);
            document.removeEventListener('mouseup', onMouseUp);
        }

        // 全屏控制
        fullscreenBtn.addEventListener('click', () => {
            const fullscreenElem = container;

            if (!document.fullscreenElement) {
                const requestFS = fullscreenElem.requestFullscreen ||
                                fullscreenElem.webkitRequestFullscreen ||
                                fullscreenElem.mozRequestFullScreen;

                if (requestFS) {
                    requestFS.call(fullscreenElem);
                    fullscreenBtn.innerHTML = '<i class="fa fa-compress"></i>';
                }
            } else {
                const exitFS = document.exitFullscreen ||
                            document.webkitExitFullscreen ||
                            document.mozCancelFullScreen;

                if (exitFS) {
                    exitFS.call(document);
                    fullscreenBtn.innerHTML = '<i class="fa fa-expand"></i>';
                }
            }
            showControls(); // 操作后显示控制栏
        });

        // 监听全屏变化
        document.addEventListener('fullscreenchange', updateFullscreenButton);
        document.addEventListener('webkitfullscreenchange', updateFullscreenButton);
        document.addEventListener('mozfullscreenchange', updateFullscreenButton);
        
        function updateFullscreenButton() {
            if (document.fullscreenElement) {
                fullscreenBtn.innerHTML = '<i class="fa fa-compress"></i>';
            } else {
                fullscreenBtn.innerHTML = '<i class="fa fa-expand"></i>';
            }
        }

        // 视频元数据加载后更新进度条
        videoElement.addEventListener('loadedmetadata', updateProgress);
        videoElement.addEventListener('timeupdate', updateProgress);
        videoElement.addEventListener('progress', updateProgress);
        videoElement.addEventListener('ended', () => {
            playPauseBtn.innerHTML = '<i class="fa fa-play"></i>';
            showControls(); // 视频结束时显示控制栏
        });

        // 鼠标移动显示控制栏
        container.addEventListener('mousemove', showControls);
        controls.addEventListener('mousemove', showControls);

        // 键盘控制
        document.addEventListener('keydown', (e) => {
            if (e.code === 'Space') {
                // 空格控制播放/暂停
                e.preventDefault();
                if (videoElement.paused) {
                    play();
                } else {
                    pause();
                }
                showControls();
            } else if (e.code === 'ArrowRight') {
                // 右箭头快进
                e.preventDefault();
                setposition(videoElement.currentTime + 10);
                showControls();
            } else if (e.code === 'ArrowLeft') {
                // 左箭头后退
                e.preventDefault();
                setposition(videoElement.currentTime - 10);
                showControls();
            } else if (e.code === 'ArrowUp') {
                // 上箭头增加音量
                e.preventDefault();
                videoElement.volume = Math.min(1, videoElement.volume + 0.1);
                volumeInput.value = videoElement.volume;
                showControls();
            } else if (e.code === 'ArrowDown') {
                // 下箭头减小音量
                e.preventDefault();
                videoElement.volume = Math.max(0, videoElement.volume - 0.1);
                volumeInput.value = videoElement.volume;
                showControls();
            } else if (e.code === 'KeyF') {
                // F键全屏
                e.preventDefault();
                fullscreenBtn.click();
            }
        });

        // 启动进度更新
        progressUpdateInterval = setInterval(updateProgress, 250);

        // 进入全屏模式
        const enterFullscreen = () => {
            const fullscreenElem = container.requestFullscreen
                ? container
                : video;

            const requestFS =
                fullscreenElem.requestFullscreen ||
                fullscreenElem.webkitRequestFullscreen ||
                fullscreenElem.mozRequestFullScreen;

            if (requestFS) {
                requestFS.call(fullscreenElem).catch(() => {
                    container.style.width = `${window.innerWidth}px`;
                    container.style.height = `${window.innerHeight}px`;
                });
            }
            video.volume = 1;
        };

        setTimeout(enterFullscreen, 300);
        
        // 初始显示控制栏
        setTimeout(() => {
            showControls();
        }, 1000);
    }

    // 检测视频元素
    function checkVideo() {
        if (Date.now() - startTime > 15000) {
            clearInterval(interval);
            return;
        }

        const video = document.querySelector('video');
        if (!video) return;

        if (video.paused) {
            video.play();
            // Try to click common play buttons if direct play fails
            const playBtns = document.querySelectorAll('.prism-play-btn, .vjs-big-play-button, .xgplayer-start, .txp_btn_play, .play-btn');
            playBtns.forEach(btn => btn.click());
        }

        if (video && video.readyState > 0) {
            clearInterval(interval);
            removeControls();
            setupVideo(video);

            if (video.videoWidth && video.videoHeight) {
                ku9.setvideo(video.videoWidth, video.videoHeight);
                ku9.setaudio("立体声");
            }
        }
    }

    // 模拟外部API
    const ku9 = {
        scaleType: 0,
        getscale: function() {
            return this.scaleType;
        },
        setvideo: function(width, height) {
            console.log(`设置视频分辨率: ${width}x${height}`);
        },
        setaudio: function(audioInfo) {
            console.log(`设置音频: ${audioInfo}`);
        },
        setposition: function(position) {
            console.log(`设置视频进度: ${position.toFixed(2)}秒`);
        },
        setduration: function(duration) {
            console.log(`设置视频总时长: ${duration.toFixed(2)}秒`);
        }
    };
    window.ku9 = ku9;

    // 启动检测
    const interval = setInterval(checkVideo, 100);

    // 移动端适配
    const viewportMeta = document.createElement('meta');
    viewportMeta.name = "viewport";
    viewportMeta.content = "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no";
    document.head.appendChild(viewportMeta);

    // 错误处理
    window.onerror = function(message, source, lineno, colno, error) {
        console.error(`Error: ${message}\nSource: ${source}\nLine: ${lineno}\nColumn: ${colno}\nError: ${error}`);
    };

    // 添加 Font Awesome
    const fontAwesome = document.createElement('link');
    fontAwesome.href = 'https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.7.2/css/all.min.css';
    fontAwesome.rel = 'stylesheet';
    document.head.appendChild(fontAwesome);
})();
    