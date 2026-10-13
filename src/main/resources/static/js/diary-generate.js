// 日記生成中のローディング表示と、画面側の二重押下の防止（仕様書 4 章「日記生成中の表示と二重押下の防止」）。
// JavaScript が無効でも、ローディング表示が出ないだけで生成はできる。
(() => {
    const body = document.getElementById('diary-body');
    const loading = document.getElementById('diary-loading');
    const content = document.getElementById('diary-content');
    const forms = document.querySelectorAll('form.js-diary-generate');
    if (!body || !loading || forms.length === 0) {
        return;
    }

    const hasDiary = content?.dataset.hasDiary === 'true';
    const LOADING_LABEL = '日記を書いています…';
    let submitting = false;

    // ボタンを元の表示に戻せるよう、最初のラベルを覚えておく
    const buttons = Array.from(forms, (form) => {
        const button = form.querySelector('button[type="submit"]');
        return { button, label: button.textContent };
    });

    const showLoading = (button) => {
        button.disabled = true;
        button.replaceChildren();
        const spinner = document.createElement('span');
        spinner.className = 'spinner-border spinner-border-sm me-2';
        spinner.setAttribute('aria-hidden', 'true');
        button.append(spinner, LOADING_LABEL);

        loading.hidden = false;
        body.setAttribute('aria-busy', 'true');
        // 日記があれば薄く表示したまま残す。日記がなければ「まだ日記はありません」を隠し、ローディングの文言だけを出す
        if (content) {
            content.classList.add(hasDiary ? 'opacity-50' : 'd-none');
        }
    };

    const reset = () => {
        submitting = false;
        buttons.forEach(({ button, label }) => {
            button.disabled = false;
            button.textContent = label;
        });
        loading.hidden = true;
        body.removeAttribute('aria-busy');
        if (content) {
            content.classList.remove('opacity-50', 'd-none');
        }
    };

    forms.forEach((form) => {
        // click ではなく submit で扱う（Enter キーでの送信も同じように扱うため）
        form.addEventListener('submit', (event) => {
            if (submitting) {
                event.preventDefault();
                return;
            }
            submitting = true;
            const button = form.querySelector('button[type="submit"]');
            // 送信を始めたあとに無効にする（先に無効にすると、ブラウザによっては送信自体が止まるため）
            setTimeout(() => showLoading(button), 0);
        });
    });

    // ブラウザの「戻る」でこの画面に戻ったときは、無効のまま固まらないように元に戻す
    window.addEventListener('pageshow', (event) => {
        if (event.persisted) {
            reset();
        }
    });
})();
