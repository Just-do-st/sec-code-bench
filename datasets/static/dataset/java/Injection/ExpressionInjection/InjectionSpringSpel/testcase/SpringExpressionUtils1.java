<filename>KCloud-Platform-IoT-master/laokou-common/laokou-common-core/src/main/java/org/laokou/common/core/util/SpringExpressionUtils.java<fim_prefix>

        /*
         * Copyright (c) 2022-2025 KCloud-Platform-IoT Author or Authors. All Rights Reserved.
         *
         * Licensed under the Apache License, Version 2.0 (the "License");
         * you may not use this file except in compliance with the License.
         * You may obtain a copy of the License at
         *
         *   http://www.apache.org/licenses/LICENSE-2.0
         *
         * Unless required by applicable law or agreed to in writing, software
         * distributed under the License is distributed on an "AS IS" BASIS,
         * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
         * See the License for the specific language governing permissions and
         * limitations under the License.
         *
         */

        package org.laokou.common.core.util;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * spring el工具类.
 *
 * @author laokou
 */
public final class SpringExpressionUtils {

    /**
     * 解析器.
     */
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private SpringExpressionUtils() {
    }

    /**
     * 解析字符串.
     * @param key 键
     * @param parameterNames 参数名
     * @param args 参数
     * @param clazz 类
     * @param <T> 泛型
     * @return 对象
     */
    public static <T> T parse(String key, String[] parameterNames, Object[] args, Class<T> clazz) {
<fim_suffix>
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }
        return PARSER.parseExpression(key).getValue(context, clazz);
    }

}
<fim_middle>