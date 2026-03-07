package org.dialectics.ai.agent;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.dialectics.ai.agent.tools.FileStorageTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "BAILIAN_KEY", matches = ".+")
public class FileStorageToolsTest {
    private ChatModel chatModel;
    private FileStorageTools fileStorageTools;

    @BeforeEach
    void setUp() {
        // Create DashScopeApi instance using the API key from environment variable
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("BAILIAN_KEY")).build();

        // Create DashScope ChatModel instance
        this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();

        this.fileStorageTools = new FileStorageTools();
    }

    @Test
    public void testGenHtml() {
        SystemMessage systemMessage = SystemMessage.builder().text(HTML_GEN_SYSTEM_PROMPT).build();
        UserMessage userMessage = UserMessage.builder().text("""
                现在用html文档展示我的以下天气数据：
                        # 秦皇岛市未来3天天气预报 (2026-03-07 至 2026-03-09)

                        ## 2026-03-07 (星期六)
                        - **白天**: 多云, 气温 2°C, 南风 1-3级
                        - **夜间**: 多云, 气温 -4°C, 南风 1-3级

                        ## 2026-03-08 (星期日)
                        - **白天**: 晴, 气温 8°C, 南风 1-3级
                        - **夜间**: 晴, 气温 -5°C, 南风 1-3级

                        ## 2026-03-09 (星期一)
                        - **白天**: 晴, 气温 5°C, 南风 1-3级
                        - **夜间**: 多云, 气温 -3°C, 南风 1-3级
                """).build();
        String htmlStr = chatModel.call(systemMessage, userMessage);
        System.out.println(htmlStr);
    }

    @Test
    public void testSaveAndGetUrl() {
        String uuid = fileStorageTools.saveFile(HTML_EXAMPLE);
        String downloadUrl = fileStorageTools.generateDownloadUrl(uuid);
        String openUrl = fileStorageTools.generateOpenUrl(uuid);

        // null -> http://localhost:18081
        assertEquals("null/public/content/download/" + uuid + "?name=resultdoc.html", downloadUrl);
        assertEquals("null/public/content/open/" + uuid, openUrl);
    }

    static final String HTML_GEN_SYSTEM_PROMPT = """
            # html多媒体文档生成

            此技能用于根据提供的数据生成美观的html可视化网页，生成内容包括文字、表格、统计图、地图等。

            ## 生成规则：
            - 根据要求以及提供的数据返回html主体
            - 若信息中包含特定app才能打开的特殊协议链接，请将其转换为二维码
            - 可生成包含表格数据的html表格主体或使用javascript绘制统计图
            - 注意数据的单位是否一致，如果不一致请尝试转换
            - 包含一个导出为excel的功能

            ## 输出格式
            无误的HTML网页的完整代码字符串

            ## 约束
            - 若使用到js、css的cdn，请使用中国大陆境内可访问的cdn地址，例如bootCDN
            - 生成内容禁止包含任何解释性的内容，仅提供生成的HTML网页代码字符串，不得有任何额外内容的追加。
            - 禁止包含任何Markdown代码块，从输出中移除```html标记。

            """;

    static final String HTML_EXAMPLE = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>秦皇岛市未来3天天气预报</title>
                <script src="https://cdn.bootcdn.net/ajax/libs/Chart.js/4.4.1/chart.umd.min.js"></script>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    body {
                        font-family: "Microsoft YaHei", sans-serif;
                        background: linear-gradient(135deg, #e0f7fa, #bbdefb);
                        color: #333;
                        line-height: 1.6;
                        padding: 20px;
                    }
                    .container {
                        max-width: 1000px;
                        margin: 0 auto;
                    }
                    header {
                        text-align: center;
                        padding: 25px 0;
                        margin-bottom: 25px;
                    }
                    h1 {
                        font-size: 2.4rem;
                        color: #0288d1;
                        margin-bottom: 10px;
                        text-shadow: 1px 1px 2px rgba(0,0,0,0.1);
                    }
                    .date-range {
                        font-size: 1.2rem;
                        color: #1976d2;
                        font-weight: bold;
                    }
                    .weather-cards {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                        gap: 20px;
                        margin-bottom: 30px;
                    }
                    .card {
                        background: white;
                        border-radius: 12px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.08);
                        overflow: hidden;
                        transition: transform 0.3s ease;
                    }
                    .card:hover {
                        transform: translateY(-5px);
                    }
                    .card-header {
                        padding: 16px 20px;
                        color: white;
                        font-weight: bold;
                        font-size: 1.3rem;
                    }
                    .sat { background: linear-gradient(to right, #4fc3f7, #0288d1); }
                    .sun { background: linear-gradient(to right, #ffcc00, #ff6d00); }
                    .mon { background: linear-gradient(to right, #81c784, #388e3c); }
                    .card-body {
                        padding: 20px;
                    }
                    .weather-item {
                        margin-bottom: 16px;
                        padding-bottom: 12px;
                        border-bottom: 1px dashed #eee;
                    }
                    .weather-item:last-child {
                        border-bottom: none;
                        margin-bottom: 0;
                        padding-bottom: 0;
                    }
                    .period {
                        font-weight: bold;
                        color: #0288d1;
                        margin-right: 8px;
                    }
                    .condition {
                        display: inline-block;
                        margin-right: 10px;
                        padding: 2px 8px;
                        border-radius: 4px;
                        font-size: 0.9em;
                        background: #e3f2fd;
                        color: #1976d2;
                    }
                    .temp {
                        font-weight: bold;
                        color: #d32f2f;
                        margin: 0 8px;
                    }
                    .wind {
                        color: #5d4037;
                    }
                    .chart-container {
                        background: white;
                        border-radius: 12px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.08);
                        padding: 20px;
                        margin-bottom: 30px;
                    }
                    h2 {
                        color: #0288d1;
                        margin-bottom: 20px;
                        text-align: center;
                        font-size: 1.8rem;
                    }
                    canvas {
                        max-width: 100%;
                        height: auto;
                    }
                    .export-btn {
                        display: block;
                        width: 200px;
                        margin: 20px auto;
                        padding: 12px 24px;
                        background: #0288d1;
                        color: white;
                        border: none;
                        border-radius: 6px;
                        font-size: 1rem;
                        cursor: pointer;
                        transition: background 0.3s;
                        text-align: center;
                    }
                    .export-btn:hover {
                        background: #01579b;
                    }
                    footer {
                        text-align: center;
                        margin-top: 30px;
                        padding: 15px;
                        color: #5d4037;
                        font-size: 0.9rem;
                    }
                    @media (max-width: 768px) {
                        .weather-cards {
                            grid-template-columns: 1fr;
                        }
                        h1 {
                            font-size: 2rem;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <header>
                        <h1>秦皇岛市未来3天天气预报</h1>
                        <div class="date-range">2026-03-07 至 2026-03-09</div>
                    </header>

                    <div class="weather-cards">
                        <!-- Day 1 -->
                        <div class="card">
                            <div class="card-header sat">2026-03-07 (星期六)</div>
                            <div class="card-body">
                                <div class="weather-item">
                                    <span class="period">白天：</span>
                                    <span class="condition">多云</span>
                                    <span class="temp">2°C</span>
                                    <span class="wind">南风 1-3级</span>
                                </div>
                                <div class="weather-item">
                                    <span class="period">夜间：</span>
                                    <span class="condition">多云</span>
                                    <span class="temp">-4°C</span>
                                    <span class="wind">南风 1-3级</span>
                                </div>
                            </div>
                        </div>

                        <!-- Day 2 -->
                        <div class="card">
                            <div class="card-header sun">2026-03-08 (星期日)</div>
                            <div class="card-body">
                                <div class="weather-item">
                                    <span class="period">白天：</span>
                                    <span class="condition">晴</span>
                                    <span class="temp">8°C</span>
                                    <span class="wind">南风 1-3级</span>
                                </div>
                                <div class="weather-item">
                                    <span class="period">夜间：</span>
                                    <span class="condition">晴</span>
                                    <span class="temp">-5°C</span>
                                    <span class="wind">南风 1-3级</span>
                                </div>
                            </div>
                        </div>

                        <!-- Day 3 -->
                        <div class="card">
                            <div class="card-header mon">2026-03-09 (星期一)</div>
                            <div class="card-body">
                                <div class="weather-item">
                                    <span class="period">白天：</span>
                                    <span class="condition">晴</span>
                                    <span class="temp">5°C</span>
                                    <span class="wind">南风 1-3级</span>
                                </div>
                                <div class="weather-item">
                                    <span class="period">夜间：</span>
                                    <span class="condition">多云</span>
                                    <span class="temp">-3°C</span>
                                    <span class="wind">南风 1-3级</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="chart-container">
                        <h2>气温趋势图</h2>
                        <canvas id="tempChart"></canvas>
                    </div>

                    <button class="export-btn" onclick="exportToExcel()">导出为 Excel</button>

                    <footer>
                        数据更新时间：2026-03-06 | 天气数据仅供参考
                    </footer>
                </div>

                <script>
                    // 初始化图表
                    document.addEventListener('DOMContentLoaded', function() {
                        const ctx = document.getElementById('tempChart').getContext('2d');

                        // 温度数据（白天/夜间）
                        const dates = ['3月7日', '3月8日', '3月9日'];
                        const dayTemps = [2, 8, 5];
                        const nightTemps = [-4, -5, -3];

                        new Chart(ctx, {
                            type: 'line',
                            data: {
                                labels: dates,
                                datasets: [
                                    {
                                        label: '白天气温 (°C)',
                                        data: dayTemps,
                                        borderColor: '#d32f2f',
                                        backgroundColor: 'rgba(211, 47, 47, 0.1)',
                                        borderWidth: 3,
                                        pointBackgroundColor: '#d32f2f',
                                        pointRadius: 6,
                                        fill: true,
                                        tension: 0.3
                                    },
                                    {
                                        label: '夜间气温 (°C)',
                                        data: nightTemps,
                                        borderColor: '#1976d2',
                                        backgroundColor: 'rgba(25, 118, 210, 0.1)',
                                        borderWidth: 3,
                                        pointBackgroundColor: '#1976d2',
                                        pointRadius: 6,
                                        fill: true,
                                        tension: 0.3
                                    }
                                ]
                            },
                            options: {
                                responsive: true,
                                maintainAspectRatio: false,
                                plugins: {
                                    legend: {
                                        position: 'top',
                                    },
                                    title: {
                                        display: false
                                    }
                                },
                                scales: {
                                    y: {
                                        beginAtZero: false,
                                        title: {
                                            display: true,
                                            text: '气温 (°C)'
                                        }
                                    },
                                    x: {
                                        title: {
                                            display: true,
                                            text: '日期'
                                        }
                                    }
                                }
                            }
                        });
                    });

                    // 导出为 Excel 功能（使用纯前端 SheetJS）
                    function exportToExcel() {
                        // 检查是否已加载 SheetJS
                        if (typeof XLSX === 'undefined') {
                            // 动态加载 SheetJS CDN（国内可用）
                            const script = document.createElement('script');
                            script.src = 'https://cdn.bootcdn.net/ajax/libs/xlsx/0.18.5/xlsx.full.min.js';
                            script.onload = () => generateExcel();
                            document.head.appendChild(script);
                        } else {
                            generateExcel();
                        }
                    }

                    function generateExcel() {
                        // 构建数据表
                        const data = [
                            ['日期', '星期', '白天天气', '白天气温', '白天风向风力', '夜间天气', '夜间气温', '夜间风向风力'],
                            ['2026-03-07', '星期六', '多云', '2°C', '南风 1-3级', '多云', '-4°C', '南风 1-3级'],
                            ['2026-03-08', '星期日', '晴', '8°C', '南风 1-3级', '晴', '-5°C', '南风 1-3级'],
                            ['2026-03-09', '星期一', '晴', '5°C', '南风 1-3级', '多云', '-3°C', '南风 1-3级']
                        ];

                        // 创建工作簿和工作表
                        const ws = XLSX.utils.aoa_to_sheet(data);
                        const wb = XLSX.utils.book_new();
                        XLSX.utils.book_append_sheet(wb, ws, "天气预报");

                        // 导出文件
                        XLSX.writeFile(wb, "秦皇岛市未来3天天气预报_202603.xlsx");
                    }
                </script>
            </body>
            </html>
            """;
}
