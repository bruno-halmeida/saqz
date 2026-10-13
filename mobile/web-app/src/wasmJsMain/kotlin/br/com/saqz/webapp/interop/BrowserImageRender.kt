@file:OptIn(ExperimentalWasmJsInterop::class)

package br.com.saqz.webapp.interop

import br.com.saqz.groups.domain.attendance.share.AttendanceShareImage
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Desenha o cartão de presença num canvas 2D e devolve o PNG em base64 (sem prefixo data:), ou "" se o
// navegador não conseguir. Mesma geometria do IOSAttendanceShareAdapter (1080 de largura, linhas de 56,
// margem de 48); `heightUnits` é o piso da altura e o conteúdo a estende se precisar, para nada ser cortado.
// Linhas longas ganham reticências em vez de vazar da imagem. Síncrono, para não gastar a ativação do toque.
@JsFun(
    """(json) => {
  try {
    const model = JSON.parse(json);
    const width = 1080;
    const margin = 48;
    const rowHeight = 56;
    const sectionsHeight = model.sections.reduce(
      (total, section) => total + 48 + Math.max(section.entries.length, 1) * rowHeight + 24, 0);
    const contentHeight = margin + 56 + 40 + 40 + 72 + sectionsHeight + margin;
    const height = Math.max(model.heightUnits * rowHeight, contentHeight);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) return '';
    const family = '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif';
    const maxWidth = width - margin * 2;
    const draw = (text, y, font, color) => {
      context.font = font;
      context.fillStyle = color;
      let line = text;
      while (line.length > 1 && context.measureText(line).width > maxWidth) {
        line = line.slice(0, -2) + '…';
      }
      context.fillText(line, margin, y);
    };
    const title = '600 48px ' + family;
    const body = '32px ' + family;
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, width, height);
    context.textBaseline = 'top';
    let y = margin;
    draw(model.title, y, title, '#000000');
    y += 56;
    draw(model.scheduleLine, y, body, '#444444');
    y += 40;
    draw(model.venueLine, y, body, '#444444');
    y += 40;
    draw(model.capacityLine, y, body, '#444444');
    y += 72;
    for (const section of model.sections) {
      draw(section.title + ' (' + section.countLabel + ')', y, title, '#000000');
      y += 48;
      const entries = section.entries.length === 0 ? [section.emptyLabel] : section.entries;
      for (const entry of entries) {
        draw(entry, y, body, '#444444');
        y += rowHeight;
      }
      y += 24;
    }
    const url = canvas.toDataURL('image/png');
    return url.slice(url.indexOf(',') + 1);
  } catch (error) {
    return '';
  }
}""",
)
private external fun jsRenderAttendanceImage(json: String): String

/** PNG (base64) do cartão de presença, ou `null` se o canvas do navegador falhar. */
internal fun renderAttendanceImageBase64(image: AttendanceShareImage): String? {
    val model = buildJsonObject {
        put("title", image.title)
        put("scheduleLine", image.scheduleLine)
        put("venueLine", image.venueLine)
        put("capacityLine", image.capacityLine)
        put("heightUnits", image.heightUnits)
        put(
            "sections",
            buildJsonArray {
                image.sections.forEach { section ->
                    add(
                        buildJsonObject {
                            put("title", section.title)
                            put("countLabel", section.countLabel)
                            put("emptyLabel", section.emptyLabel)
                            put("entries", JsonArray(section.entries.map(::JsonPrimitive)))
                        },
                    )
                }
            },
        )
    }
    return runCatching { jsRenderAttendanceImage(model.toString()) }.getOrNull()?.takeIf { it.isNotEmpty() }
}
