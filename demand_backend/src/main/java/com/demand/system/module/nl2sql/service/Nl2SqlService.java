package com.demand.system.module.nl2sql.service;

import com.demand.system.module.nl2sql.dto.Nl2SqlContext;
import com.demand.system.module.nl2sql.dto.Nl2SqlOutcome;

/**
 * NL2SQL 数据问答服务：把自然语言问题翻译为只读 SQL，执行后生成整合性中文回答。
 *
 * <p>完整链路：意图判定 → Schema 注入 → LLM 生成 SQL → 安全校验 → 数据权限注入 → 执行 → 总结回答。</p>
 */
public interface Nl2SqlService {

    /** 能力是否启用（关闭时助手直接跳过该链路）。 */
    boolean isEnabled();

    /** 轻量规则判断问题是否"很像"数据查询（用于自动路由）。 */
    boolean looksLikeDataQuery(String question);

    /**
     * 执行一次数据问答。
     *
     * @param context       执行上下文
     * @param listener      进度回调（可传 {@link Nl2SqlProgressListener#NOOP}）
     * @param answerStream  回答流式回调；为 null 时只产出结构化结果、不生成自然语言
     * @return 执行结果；{@code isDataQuery=false} 表示判定为非数据问题，调用方应回退到其它链路
     */
    Nl2SqlOutcome query(Nl2SqlContext context,
                        Nl2SqlProgressListener listener,
                        Nl2SqlAnswerStream answerStream);
}
