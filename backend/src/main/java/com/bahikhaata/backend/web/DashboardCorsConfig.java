/*
 * bahi-khaata — point of sale for Bachat Baazar
 * Copyright (C) 2026 Aakash Sandhyan
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.bahikhaata.backend.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the admin dashboard, a separate web app, call this backend from the browser.
 *
 * <p>The terminal needs none of this — it is a desktop program making plain HTTP calls, with no
 * origin and no browser to enforce one. The dashboard is a page, so the browser blocks its
 * requests to another origin unless that origin says otherwise. This is that permission, and
 * nothing more.
 *
 * <p>Scoped as tightly as it usefully can be: only the dashboard's own origins, only the admin
 * paths it actually calls. It is not {@code *}. A blanket allow would let any page a browser
 * happens to load talk to the shop's backend, which on a machine that also browses the web is a
 * door left open for no reason.
 *
 * <p>The origins are the dashboard's dev server and the address it is served from in the shop.
 * When the dashboard moves behind a real host, that host is added here and the dev one dropped.
 */
@Configuration
public class DashboardCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Any address on a private network, on the dashboard's port. The shop's phones and
        // tablets load the dashboard from the PC's LAN address, so their origin is that address
        // on :5173, not localhost. Patterns rather than a fixed list because the PC's address
        // can change; scoped to the private ranges (192.168, 10, 172.16-31) and the one port,
        // so this is not a door open to the whole internet — only to the shop's own network.
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://192.168.*:5173",
                        "http://10.*:5173",
                        "http://172.16.*:5173",
                        "http://172.17.*:5173",
                        "http://172.18.*:5173",
                        "http://172.19.*:5173",
                        "http://172.2*.*:5173",
                        "http://172.3*.*:5173")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type");
    }
}
