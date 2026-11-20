// 写真の欄（input.js-photo-input）。送る前にブラウザで形式と大きさを確かめ、受け付けないファイルは選び直してもらう。
// サーバーでも同じ決まりで確かめる（docs/phase3-spec.md の「5. 画面 > 今日のつぶやき（変更。`templates/home.html`、`/`）」）。
// 決まりとメッセージは、サーバーが data 属性で渡す（数字や文言をここに書かない）。
//
// - accept：受け付ける形式（image/jpeg,image/png）
// - data-max-bytes：大きさの上限（バイト）
// - data-type-message・data-too-large-message：断るときのメッセージ
// - data-error：メッセージを出す要素の id
//
// JavaScript が無効でも、サーバーが同じ決まりで断るので、写真は送れる。
(() => {
    document.querySelectorAll('input.js-photo-input').forEach((input) => {
        const error = document.getElementById(input.dataset.error);
        const accepted = input.accept.split(',').map((type) => type.trim()).filter(Boolean);
        const maxBytes = Number(input.dataset.maxBytes);

        const showError = (message) => {
            if (error) {
                error.textContent = message;
                error.hidden = !message;
            }
            input.classList.toggle('is-invalid', Boolean(message));
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
            input.dispatchEvent(new CustomEvent('photo:checked', { detail: { accepted: Boolean(file) && !message } }));
        });
    });
})();
