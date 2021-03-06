/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>
 */

package net.adamcin.recap.util;

import net.adamcin.recap.api.RequestDepthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author madamcin
 * @version $Id: DefaultRequestDepthConfig.java$
 */
public class DefaultRequestDepthConfig implements RequestDepthConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRequestDepthConfig.class);

    private final Map<String, Integer> depthByPath;
    private final List<Integer> depthByDepth;

    public DefaultRequestDepthConfig(Map<String, Integer> depthByPath, List<Integer> depthByDepth) {
        this.depthByPath = depthByPath;
        this.depthByDepth = depthByDepth;
    }

    public int getRequestDepth(String jcrPath, int pathDepth) {
        if (depthByPath != null && depthByPath.containsKey(jcrPath)) {
            return depthByPath.get(jcrPath);
        } else if (depthByDepth != null && depthByDepth.size() > 0) {
            int depth = depthByDepth.get(depthByDepth.size() - 1);

            if (depthByDepth.size() > pathDepth) {
                return depthByDepth.get(pathDepth);
            }

            return depth;
        } else {
            return 1;
        }
    }

    /**
     *
     * @param parameterValue
     * @return a new DefaultRequestDepthConfig parsed from the parameter value
     */
    public static DefaultRequestDepthConfig parseParameterValue(String parameterValue) {
        Map<String, Integer> depthByPath = new HashMap<String, Integer>();
        List<Integer> depthByDepth = new ArrayList<Integer>();

        if (parameterValue != null) {
            StringTokenizer tk = new StringTokenizer(parameterValue);
            if (tk.hasMoreTokens()) {
                String nextToken = tk.nextToken();
                LOGGER.debug("[parseParameterValue] parsing token: {}", nextToken);
                if (nextToken.contains("=")) {
                    String[] depthByPathParts = nextToken.split("=", 2);

                    try {
                        String depthByPathPath = depthByPathParts[0];
                        Integer depthByPathDepth = Integer.valueOf(depthByPathParts[1]);
                        depthByPath.put(depthByPathPath, depthByPathDepth);
                    } catch (NumberFormatException e) {
                        LOGGER.error("[parseParameterValue] failed to parse depthByPath: {}", nextToken);
                    }
                } else {
                    try {
                        depthByDepth.add(Integer.valueOf(nextToken));
                    } catch (NumberFormatException e) {
                        LOGGER.error("[parseParameterValue] failed to parse depthByDepth: {}", nextToken);
                    }
                }
            }
        }

        return new DefaultRequestDepthConfig(depthByPath, depthByDepth);
    }

    @Override
    public String toString() {
        return "DefaultRequestDepthConfig{" +
                "depthByPath=" + depthByPath +
                ", depthByDepth=" + depthByDepth +
                '}';
    }
}
