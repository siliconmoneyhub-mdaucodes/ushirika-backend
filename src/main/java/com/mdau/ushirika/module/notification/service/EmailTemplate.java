package com.mdau.ushirika.module.notification.service;

/**
 * Wraps every outgoing email body in the site's actual branding (logo, cream/green palette,
 * footer) — colors pulled directly from the live site's computed styles, not guessed. Applied
 * centrally in BrevoEmailService.sendPlain() so every call site across the app gets it for free.
 */
final class EmailTemplate {

    static final String PRIMARY_GREEN = "#007834";
    static final String CREAM_BG = "#f8ebd5";
    static final String SAGE_BG = "#bfd9c3";
    static final String TEXT_DARK = "#0a150b";
    static final String WHITE = "#fcfcf9";

    private static final String LOGO_URL = "https://ushirikacommunity.site/images/logo-without-bg.png";

    private EmailTemplate() {}

    static String wrap(String bodyHtml) {
        String content = looksLikeHtml(bodyHtml) ? bodyHtml : plainTextToHtml(bodyHtml);
        return """
                <!doctype html>
                <html>
                <body style="margin:0;padding:32px 16px;background:%s;font-family:-apple-system,Segoe UI,Roboto,Arial,sans-serif;color:%s">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;margin:0 auto">
                    <tr>
                      <td style="text-align:center;padding-bottom:24px">
                        <img src="%s" alt="Ushirika Welfare Organization" width="72" style="display:inline-block" />
                        <div style="font-size:20px;font-weight:700;color:%s;margin-top:8px">Ushirika Welfare Organization</div>
                      </td>
                    </tr>
                    <tr>
                      <td style="background:%s;border-radius:16px;padding:32px;box-shadow:0 1px 3px rgba(0,0,0,0.08)">
                        %s
                      </td>
                    </tr>
                    <tr>
                      <td style="text-align:center;padding-top:24px;color:%s;font-size:13px;opacity:0.75">
                        <div style="font-style:italic;margin-bottom:8px">"We Are A Caring Heart With A Helping Hand."</div>
                        <div>Ushirika Welfare Organization &middot; Dallas&ndash;Fort Worth, TX</div>
                        <div><a href="mailto:info@ushirika-welfare.org" style="color:%s">info@ushirika-welfare.org</a> &middot; +1 817 703 1821</div>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(CREAM_BG, TEXT_DARK, LOGO_URL, PRIMARY_GREEN, WHITE, content, TEXT_DARK, PRIMARY_GREEN);
    }

    private static boolean looksLikeHtml(String s) {
        return s != null && s.stripLeading().startsWith("<");
    }

    private static String plainTextToHtml(String text) {
        if (text == null) return "";
        StringBuilder html = new StringBuilder();
        for (String para : text.split("\n\n")) {
            html.append("<p style=\"margin:0 0 16px;line-height:1.5\">")
                    .append(para.replace("\n", "<br>"))
                    .append("</p>");
        }
        return html.toString();
    }
}
