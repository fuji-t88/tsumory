// 音声入力（.js-voice-input）。docs/phase3-spec.md の「5. 画面 > 音声入力」。
//
// ブラウザの音声認識（Web Speech API の SpeechRecognition）で話した言葉を文字にし、確定した文字だけを本文の欄に足す。
// 音声そのものは、このスクリプトからはどこにも送らない（サーバーの API も呼ばない）。ブラウザによっては、ブラウザが外部の認識サービスに送る。
//
// - data-target：文字を足す本文の欄（textarea）の id
// - data-unsupported：音声認識に対応していないときに出す案内の id
// - data-text-*・data-error-*：状態とエラーの文言（画面の文言はテンプレートに置く）
//
// 状態は「待機」→「聞き取り中」→「終了中」→「待機」。聞き取り中は本文の欄を読み取り専用にする（手の入力と音声の結果をぶつけない）。
// - 停止：確定した文字を残す
// - キャンセル：始める前の本文に戻す（その回に認識した文字はすべて捨てる）
(() => {
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;

    // 本文に入れられない文字を除く。改行と行・段落の区切りは空白にし、制御文字と書式文字は取り除く（絵文字をつなぐ U+200D は残す）。
    // サーバーでも同じ決まりで確かめる（Post.INVISIBLE_CHAR_CLASS）
    const normalize = (text) => text
        .replace(/\r\n|[\r\n\u2028\u2029]/g, ' ')
        .replace(/[\p{Cc}\p{Cf}]/gu, (c) => (c === '\u200D' ? c : ''));

    document.querySelectorAll('.js-voice-input').forEach((box) => {
        const target = document.getElementById(box.dataset.target);
        const unsupported = box.dataset.unsupported ? document.getElementById(box.dataset.unsupported) : null;
        if (!target) {
            return;
        }
        if (!Recognition) {
            // 対応していないブラウザでは、ボタンを出さずに案内だけを出す（キーボードの入力はそのまま使える）
            if (unsupported) {
                unsupported.hidden = false;
            }
            return;
        }
        box.hidden = false;

        const startButton = box.querySelector('.js-voice-start');
        const stopButton = box.querySelector('.js-voice-stop');
        const cancelButton = box.querySelector('.js-voice-cancel');
        const status = box.querySelector('.js-voice-status');
        const text = box.dataset;

        let recognition = null;
        let state = 'idle';
        let baseText = '';
        let finalText = '';
        let cancelled = false;
        let errorMessage = '';

        const setState = (next, message) => {
            state = next;
            startButton.hidden = next !== 'idle';
            stopButton.hidden = next === 'idle';
            cancelButton.hidden = next === 'idle';
            stopButton.disabled = next !== 'listening';
            cancelButton.disabled = next !== 'listening';
            target.readOnly = next !== 'idle';
            status.textContent = message;
        };

        const errorOf = (error) => {
            switch (error) {
                case 'not-allowed':
                case 'service-not-allowed':
                    return text.errorNotAllowed;
                case 'audio-capture':
                    return text.errorAudioCapture;
                case 'no-speech':
                    return text.errorNoSpeech;
                case 'network':
                    return text.errorNetwork;
                default:
                    return text.errorOther;
            }
        };

        const render = () => {
            target.value = baseText + finalText;
        };

        const finish = () => {
            recognition = null;
            if (cancelled) {
                target.value = baseText;
            }
            const message = errorMessage
                || (cancelled ? text.textCancelled : (finalText ? text.textDone : text.textIdle));
            setState('idle', message);
            target.focus();
        };

        startButton.addEventListener('click', () => {
            // 連打しても、聞き取りを 2 つ同時に始めない
            if (state !== 'idle' || target.disabled) {
                return;
            }
            baseText = target.value;
            finalText = '';
            cancelled = false;
            errorMessage = '';
            recognition = new Recognition();
            recognition.lang = 'ja-JP';
            recognition.continuous = true;
            recognition.interimResults = true;
            recognition.onresult = (event) => {
                let interim = '';
                for (let i = event.resultIndex; i < event.results.length; i += 1) {
                    const transcript = normalize(event.results[i][0].transcript);
                    if (event.results[i].isFinal) {
                        finalText += transcript;
                    } else {
                        interim += transcript;
                    }
                }
                // 確定した文字だけを本文に入れる。確定前の文字は、状態の欄に途中経過として出すだけ
                render();
                status.textContent = interim ? `${text.textListening}（${interim}）` : text.textListening;
            };
            recognition.onerror = (event) => {
                // キャンセル（abort）でも aborted のエラーが来るので、そのときは出さない
                if (event.error !== 'aborted') {
                    errorMessage = errorOf(event.error);
                }
            };
            recognition.onend = finish;
            try {
                recognition.start();
                setState('listening', text.textListening);
            } catch (e) {
                recognition = null;
                setState('idle', text.errorOther);
            }
        });

        stopButton.addEventListener('click', () => {
            if (state !== 'listening' || !recognition) {
                return;
            }
            setState('stopping', text.textStopping);
            recognition.stop();
        });

        cancelButton.addEventListener('click', () => {
            if (state !== 'listening' || !recognition) {
                return;
            }
            cancelled = true;
            setState('stopping', text.textStopping);
            recognition.abort();
        });

        // 画面を離れるとき（ほかの画面へ移る、再読み込み）は、聞き取りを止める
        window.addEventListener('pagehide', () => {
            if (recognition) {
                cancelled = true;
                recognition.abort();
            }
        });
    });
})();
