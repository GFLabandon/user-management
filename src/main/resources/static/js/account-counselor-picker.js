(() => {
    const picker = document.querySelector('.counselor-picker');
    if (!picker) return;
    const keyword = document.getElementById('counselor-keyword');
    const selectedId = document.getElementById('counselorId');
    const selectedLabel = document.getElementById('selected-counselor');
    const clear = document.getElementById('clear-counselor');
    const results = document.getElementById('counselor-results');
    const status = document.getElementById('picker-status');
    let generation = 0;
    let pending;

    function resetResults() {
        generation++;
        pending?.abort();
        results.replaceChildren();
    }
    async function search() {
        resetResults();
        const term = keyword.value.trim();
        if (!term) {
            status.textContent = '请输入工号或姓名后搜索。';
            return;
        }
        const current = generation;
        pending = new AbortController();
        const url = new URL(picker.dataset.searchUrl, window.location.href);
        url.searchParams.set('keyword', term);
        if (picker.dataset.accountId) url.searchParams.set('accountId', picker.dataset.accountId);
        status.textContent = '正在查找…';
        try {
            const response = await fetch(url, {signal: pending.signal, headers: {Accept: 'application/json'}});
            if (response.redirected) throw new Error('登录已失效，请重新登录后搜索。');
            if (response.status === 403) throw new Error('没有账号管理权限，请联系管理员。');
            if (!response.ok || !response.headers.get('Content-Type')?.includes('application/json')) {
                throw new Error('搜索失败，请稍后重试。');
            }
            const options = await response.json();
            if (current !== generation) return;
            status.textContent = options.length === 0 ? '没有可关联的匹配档案，请更换关键词；已被其他账号关联的档案不会显示。'
                : options.length === 20 ? '显示前 20 条，请细化工号或姓名缩小范围。' : `找到 ${options.length} 条，请点击选择。`;
            for (const option of options) {
                const item = document.createElement('li');
                const button = document.createElement('button');
                const label = `${option.name} · ${option.employeeNo} · ${option.departmentName}`;
                button.type = 'button';
                button.className = 'button button-ghost';
                button.textContent = label;
                button.addEventListener('click', () => {
                    selectedId.value = option.id;
                    selectedLabel.textContent = `已选择：${label}`;
                    clear.disabled = false;
                    resetResults();
                    status.textContent = '已选择档案，保存账号后生效。';
                    clear.focus();
                });
                item.append(button);
                results.append(item);
            }
        } catch (error) {
            if (current === generation && error.name !== 'AbortError') {
                status.textContent = error instanceof TypeError ? '网络异常，请稍后重试。' : error.message;
            }
        }
    }
    document.getElementById('search-counselors').addEventListener('click', search);
    keyword.addEventListener('keydown', event => {
        if (event.key === 'Enter' && !event.isComposing) {
            event.preventDefault();
            search();
        }
    });
    keyword.addEventListener('input', () => {
        resetResults();
        status.textContent = '关键词已更改，请搜索；当前选择保持不变。';
    });
    clear.addEventListener('click', () => {
        resetResults();
        selectedId.value = '';
        selectedLabel.textContent = '未关联档案';
        clear.disabled = true;
        status.textContent = '已取消选择，保存账号后生效。';
        keyword.focus();
    });
})();
