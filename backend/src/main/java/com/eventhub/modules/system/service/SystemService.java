package com.eventhub.modules.system.service;

import com.eventhub.modules.system.dto.request.EchoRequest;
import com.eventhub.modules.system.vo.EchoInfo;
import com.eventhub.modules.system.vo.PingInfo;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * 系统模块服务层。
 * 该类承接 {@code SystemController} 发起的系统基础能力调用，
 * 负责组装探活信息、构造回显结果，并将控制器层与底层环境访问细节隔离开。
 * 这样可以让 Controller 只关注 HTTP 协议、参数绑定和统一响应包装，
 * 而把“返回什么业务数据”这一层职责收敛在 Service 中。
 *
 * <p>这里当前没有额外声明 {@code SystemService} 接口，而是直接使用具体类，
 * 是一个有意的简化选择，而不是遗漏：
 * 第一，当前仓库还处在单体后端基础阶段，这个服务只有唯一实现，也没有多数据源、多提供方切换等场景；
 * 第二，这里的方法非常简单，先抽接口只会增加一个同名接口和实现类的样板代码，却没有带来实际解耦收益；
 * 第三，Spring 依赖注入并不要求每个 Service 都必须先有接口，按具体类注入在这种单实现场景下完全成立。
 *
 * <p>后续如果这里真的出现以下需求，再补接口会更合适：
 * 例如需要多套实现并按环境切换、需要为应用层与领域层建立明确端口边界、
 * 或者某个能力要被抽象成可替换的协作契约。也就是说，接口更适合在“确实存在抽象点”时引入，
 * 而不是在项目刚起步、只有单一实现时为了形式统一提前铺开。
 *
 * <p>{@link RequiredArgsConstructor} 会为 {@code final} 依赖字段生成构造器。
 * 这里仍然坚持构造器注入，避免字段注入带来的不可测试和依赖不透明问题，只是减少手写样板代码。
 */
@Service
@RequiredArgsConstructor
public class SystemService {

    /**
     * Spring 环境抽象。
     * 这里通过 {@link Environment} 读取应用名称和激活配置，
     * 避免把配置访问逻辑直接散落到控制器中。
     */
    private final Environment environment;

    /**
     * 生成系统探活结果。
     * 该方法会返回当前服务名称、激活环境列表和服务端时间，
     * 用于支持最基础的“服务是否存活、当前实例运行在哪个配置环境中”的探测诉求。
     *
     * @return 系统探活信息
     */
    public PingInfo ping() {
        return new PingInfo(
                environment.getProperty("spring.application.name", "eventhub-backend"),
                resolveActiveProfiles(),
                OffsetDateTime.now()
        );
    }

    /**
     * 构造回显结果。
     * 该方法不引入额外业务规则，而是把请求中的消息和标签原样写回，
     * 并补充服务端生成的回显时间，便于验证请求体绑定、参数校验和统一响应链路是否正常。
     *
     * @param request 回显请求参数
     * @return 回显结果对象
     */
    public EchoInfo echo(EchoRequest request) {
        return new EchoInfo(request.message(), request.tag(), OffsetDateTime.now());
    }

    /**
     * 解析当前激活的 Spring Profile 列表。
     * 如果应用没有显式激活任何 Profile，则返回 {@code default}，
     * 这样接口调用方看到返回结果时可以更直观地理解当前实例并非“缺数据”，
     * 而是运行在 Spring 的默认配置语义下。
     *
     * @return 当前激活的配置环境列表；若未显式指定，则返回仅包含 {@code default} 的列表
     */
    private List<String> resolveActiveProfiles() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            // Spring 未显式激活 profile 时，使用 default 作为更直观的返回值。
            return List.of("default");
        }
        return Arrays.asList(activeProfiles);
    }
}
