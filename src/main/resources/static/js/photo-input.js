// 写真の欄（input.js-photo-input）。docs/phase3-spec.md の「5. 画面 > 今日のつぶやき（変更。`templates/home.html`、`/`）」。
//
// - 送る前にブラウザで形式と大きさを確かめ、受け付けないファイルは選ばなかったことにする（サーバーでも同じ決まりで確かめる）
// - 選んだ写真をプレビューし、「写真を外す」で選択を取り消せるようにする（PH-REQ-04）
//
// 決まりとメッセージは、サーバーが data 属性で渡す（数字や文言をここに書かない）。
//
// - accept：受け付ける形式（image/jpeg,image/png）
// - data-max-bytes：大きさの上限（バイト）
// - data-type-message・data-too-large-message：断るときのメッセージ
// - data-error：メッセージを出す要素の id
// - data-preview：プレビューの要素の id。中に img と、解除のボタン（.js-photo-clear）を置く
//
// JavaScript が無効でも、写真は送れる（プレビューが出ないだけ。決まりはサーバーが確かめる）。
(() => {
    document.querySelectorAll('input.js-photo-input').forEach((input) => {
        const error = document.getElementById(input.dataset.error);
        const preview = input.dataset.preview ? document.getElementById(input.dataset.preview) : null;
        const image = preview?.querySelector('img');
        const accepted = input.accept.split(',').map((type) => type.trim()).filter(Boolean);
        const maxBytes = Number(input.dataset.maxBytes);
        let objectUrl = null;

        const showError = (message) => {
            if (error) {
                error.textContent = message;
                error.hidden = !message;
            }
            input.classList.toggle('is-invalid', Boolean(message));
        };

        const hidePreview = () => {
            if (objectUrl) {
                URL.revokeObjectURL(objectUrl);
                objectUrl = null;
            }
            image?.removeAttribute('src');
            if (preview) {
                preview.hidden = true;
            }
        };

        const showPreview = (file) => {
            if (!preview || !image) {
                return;
            }
            hidePreview();
            objectUrl = URL.createObjectURL(file);
            image.src = objectUrl;
            preview.hidden = false;
        };

        const rejectionOf = (file) => {
            if (accepted.length > 0 && !accepted.includes(file.type)) {
                return input.dataset.typeMessage;
            }
            if (maxBytes > 0 && file.size > maxBytes) {
                return input.dataset.tooLargeMessage;
            }
            return '';
        };

        input.addEventListener('change', () => {
            const file = input.files[0];
            const message = file ? rejectionOf(file) : '';
            if (message) {
                // 受け付けないファイルは選んでいないことにする（そのまま送らせない）
                input.value = '';
            }
            showError(message);
            if (file && !message) {
                showPreview(file);
            } else {
                hidePreview();
            }
        });

        preview?.querySelector('.js-photo-clear')?.addEventListener('click', () => {
            input.value = '';
            showError('');
            hidePreview();
            // 外したあとも、キーボードで続けて操作できるように写真の欄へ戻す
            input.focus();
        });
    });
})();
