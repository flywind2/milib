/*
 * Copyright 2016 MiLaboratory.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.milaboratory.util;

import java.text.DecimalFormat;

public class TimeUtils {
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");

    public static String nanoTimeToString(long t) {
        double v = t;
        if ((t /= 1000) == 0)
            return "" + DECIMAL_FORMAT.format(v) + "ns";

        v /= 1000;
        if ((t /= 1000) == 0)
            return "" + DECIMAL_FORMAT.format(v) + "us";

        v /= 1000;
        if ((t /= 1000) == 0)
            return "" + DECIMAL_FORMAT.format(v) + "ms";

        v /= 1000;
        if ((t /= 60) == 0)
            return "" + DECIMAL_FORMAT.format(v) + "s";

        v /= 60;
        return "" + DECIMAL_FORMAT.format(v) + "m";
    }
}