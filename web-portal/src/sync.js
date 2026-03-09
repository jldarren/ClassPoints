/**
 * ClassPoints 数据同步逻辑 (Web 端)
 */
const ClassPointsSync = {
    /**
     * 从 Android 端拉取真实学生数据
     */
    async syncData(deviceIp) {
        try {
            const url = deviceIp.startsWith('http') ? deviceIp : `http://${deviceIp}`;
            const response = await fetch(`${url}/api/sync`);
            if (!response.ok) throw new Error(`HTTP Error: ${response.status}`);
            return await response.json();
        } catch (error) {
            throw new Error(`无法连接设备: ${error.message}`);
        }
    },

    /**
     * 获取评分规则
     */
    async getRules(deviceIp) {
        try {
            const url = deviceIp.startsWith('http') ? deviceIp : `http://${deviceIp}`;
            const response = await fetch(`${url}/api/rules`);
            if (!response.ok) throw new Error(`HTTP Error: ${response.status}`);
            return await response.json();
        } catch (error) {
            console.error("Rules fetch failed:", error);
            return {};
        }
    },

    /**
     * 应用评分
     */
    async applyRule(deviceIp, studentId, rule) {
        try {
            const url = deviceIp.startsWith('http') ? deviceIp : `http://${deviceIp}`;
            const response = await fetch(`${url}/api/apply_rule`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: `postData=${encodeURIComponent(JSON.stringify({
                    studentId: studentId,
                    category: rule.category,
                    description: rule.description,
                    score: rule.score
                }))}`
            });
            if (!response.ok) throw new Error(`HTTP Error: ${response.status}`);
            return await response.json();
        } catch (error) {
            throw new Error(`申请评分失败: ${error.message}`);
        }
    },

    /**
     * 获取单个学生的评分历史记录 (用于图表)
     */
    async getStudentDetails(deviceIp, studentId) {
        try {
            const url = deviceIp.startsWith('http') ? deviceIp : `http://${deviceIp}`;
            const response = await fetch(`${url}/api/student_details?id=${studentId}`);
            if (!response.ok) throw new Error(`HTTP Error: ${response.status}`);
            return await response.json();
        } catch (error) {
            console.error("History fetch failed:", error);
            return [];
        }
    }
};

window.ClassPointsSync = ClassPointsSync;
