// 残り文字数の表示。textarea の data-counter に、残り文字数を出す要素の id を書く。
// value.length は UTF-16 の単位で数え、maxlength と同じ数え方になる。
// ただし改行は 1 文字と数えるので、ブラウザが \r\n で送る改行を 2 文字と数えるサーバーの @Size とはずれる。
document.querySelectorAll('textarea[data-counter]').forEach((textarea) => {
    const counter = document.getElementById(textarea.dataset.counter);
    const update = () => {
        counter.textContent = textarea.maxLength - textarea.value.length;
    };
    textarea.addEventListener('input', update);
    update();
});

// 確認ダイアログ。form の data-confirm に文言を書き、OK のときだけ送信する。
document.querySelectorAll('form[data-confirm]').forEach((form) => {
    form.addEventListener('submit', (event) => {
        if (!window.confirm(form.dataset.confirm)) {
            event.preventDefault();
        }
    });
});
