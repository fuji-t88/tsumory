// 今日のつぶやき画面のフォームの送信まわり。インラインのイベントハンドラ（onsubmit・onchange）を書かずに、ここで付ける
// （CSP で unsafe-inline を禁止できるようにするため。docs/phase2-spec.md の「今日のつぶやき（変更）」）。
//
// - form.js-submit-once：送信したらボタンを無効にし、2 回目以降の送信を止める（二重送信の防止）。
//     data-submitting-label があれば、ボタンの文言をスピナー付きでその文言に変える。
//     data-loading があれば、その id の要素（「作成中です」などの案内）を表示する。
// - select.js-auto-submit：選び直したらすぐにフォームを送る（カテゴリの変更。送信ボタンは置かない）
//
// JavaScript が無効でも、送信中の表示が出ないだけで送信はできる（カテゴリの変更だけは送れない）。
(() => {
    let submitting = false;

    // ボタンを元の状態に戻せるよう、最初の文言と無効かどうか（上限に達したなど、サーバーが無効にしたもの）を覚えておく
    const states = Array.from(document.querySelectorAll('form.js-submit-once'), (form) => {
        const button = form.querySelector('button[type="submit"]');
        const loading = form.dataset.loading ? document.getElementById(form.dataset.loading) : null;
        return { form, button, loading, label: button?.textContent, disabled: button?.disabled ?? false };
    });

    const showSubmitting = ({ form, button, loading }) => {
        if (button) {
            button.disabled = true;
            const label = form.dataset.submittingLabel;
            if (label) {
                const spinner = document.createElement('span');
                spinner.className = 'spinner-border spinner-border-sm me-2';
                spinner.setAttribute('aria-hidden', 'true');
                button.replaceChildren(spinner, label);
            }
        }
        if (loading) {
            loading.hidden = false;
        }
    };

    const reset = () => {
        submitting = false;
        states.forEach(({ button, loading, label, disabled }) => {
            if (button) {
                button.disabled = disabled;
                button.textContent = label;
            }
            if (loading) {
                loading.hidden = true;
            }
        });
    };

    states.forEach((state) => {
        // click ではなく submit で扱う（Enter キーでの送信も同じように扱うため）
        state.form.addEventListener('submit', (event) => {
            if (submitting) {
                event.preventDefault();
                return;
            }
            submitting = true;
            // 送信を始めたあとに無効にする（先に無効にすると、ブラウザによっては送信自体が止まるため）
            setTimeout(() => showSubmitting(state), 0);
        });
    });

    document.querySelectorAll('select.js-auto-submit').forEach((select) => {
        select.addEventListener('change', () => select.form.submit());
    });

    // ブラウザの「戻る」でこの画面に戻ったときは、無効のまま固まらないように元に戻す
    window.addEventListener('pageshow', (event) => {
        if (event.persisted) {
            reset();
        }
    });
})();
