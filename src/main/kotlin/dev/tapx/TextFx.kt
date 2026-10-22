package dev.tapx

/**
 * Animated text for the tab list and chat, done by the TapX UI resource pack: its text shader
 * (rendertype_text.vsh, used for all GUI text) looks for a few reserved "marker" colours and draws
 * them animated instead, in a vibrant "bubbly" style: every letter gets a candy gloss (lighter at
 * the top, deeper at the bottom), its colours are boosted, a soft shine sweeps across, and tags
 * "puff": each letter swells a little around its own centre and settles back, the swell rippling
 * along the word like bubbles inflating (letters never move up or down). Some styles also fade
 * through their colours (or the rainbow) over time. Each marker is the style's own colour nudged a
 * few steps, so players without the pack simply see the normal static colour. The shader is
 * generated from [STYLES], so the markers used in messages and the shader always match. Shadows
 * are matched too (vanilla draws them at exactly a quarter of the colour).
 */
object TextFx {
    /** SHINE: one colour; CYCLE: fades c1 -> c2 -> c3 over time; RAINBOW: fades through every hue. */
    enum class Kind(val id: Int) { SHINE(0), CYCLE(1), RAINBOW(2) }

    class Style(
        val key: String, val kind: Kind, val c1: Int, val c2: Int = c1, val c3: Int = c2,
        /** Animation speed (1 = default), how strong the white shine is and how much letters puff (0..1). */
        val speed: Double = 1.0, val shine: Double = 1.0, val puff: Double = 0.0,
    ) {
        /** Set from the style's position in [STYLES]: c1 nudged by a few steps in green and blue. */
        var marker = 0
    }

    // vibrant TapX theme
    private val GOLD = 0xffd21a; private val ORANGE = 0xff7a12; private val PINK = 0xff3d8a
    private val OWNER = Triple(0xa63cff, 0xff3cd2, 0x6f3cff)
    private val ELITE = Triple(0xff2e3e, 0xff8a12, 0xff2e8a)
    private val TITAN = Triple(0xff3cf0, 0xa63cff, 0xff7aff)

    val STYLES: List<Style> = listOf(
        // rank and staff tags: bold colours, shine and puff
        Style("tag:owner", Kind.CYCLE, OWNER.first, OWNER.second, OWNER.third, puff = 1.0),
        Style("tag:admin", Kind.SHINE, 0xff2e2e, puff = 1.0),
        Style("tag:mod", Kind.SHINE, 0x2fa8ff, puff = 1.0),
        Style("tag:helper", Kind.SHINE, 0x3fe05a, puff = 1.0),
        Style("tag:vip", Kind.SHINE, 0xffa31a, puff = 1.0),
        Style("tag:pro", Kind.SHINE, 0x3dff4a, puff = 1.0),
        Style("tag:mvp", Kind.SHINE, 0x2ff2ff, puff = 1.0),
        Style("tag:elite", Kind.CYCLE, ELITE.first, ELITE.second, ELITE.third, puff = 1.0),
        Style("tag:titan", Kind.CYCLE, TITAN.first, TITAN.second, TITAN.third, puff = 1.0),
        Style("tag:legend", Kind.RAINBOW, 0xffd21a, puff = 1.0),
        // names: same colours, softer and slower, a smaller puff
        Style("name:owner", Kind.CYCLE, OWNER.first, OWNER.second, OWNER.third, speed = 0.6, shine = 0.35, puff = 0.4),
        Style("name:admin", Kind.SHINE, 0xff2e2e, speed = 0.7, shine = 0.4, puff = 0.4),
        Style("name:mod", Kind.SHINE, 0x2fa8ff, speed = 0.7, shine = 0.4, puff = 0.4),
        Style("name:helper", Kind.SHINE, 0x3fe05a, speed = 0.7, shine = 0.4, puff = 0.4),
        Style("name:vip", Kind.SHINE, 0xffa31a, speed = 0.7, shine = 0.4, puff = 0.4),
        Style("name:pro", Kind.SHINE, 0x3dff4a, speed = 0.7, shine = 0.4, puff = 0.4),
        Style("name:mvp", Kind.SHINE, 0x2ff2ff, speed = 0.7, shine = 0.4, puff = 0.4),
        Style("name:elite", Kind.CYCLE, ELITE.first, ELITE.second, ELITE.third, speed = 0.6, shine = 0.35, puff = 0.4),
        Style("name:titan", Kind.CYCLE, TITAN.first, TITAN.second, TITAN.third, speed = 0.6, shine = 0.35, puff = 0.4),
        Style("name:legend", Kind.RAINBOW, 0xffd21a, speed = 0.6, puff = 0.4),
        // tab list and chat chrome, in the TapX gold -> orange -> pink theme
        Style("title", Kind.CYCLE, GOLD, ORANGE, PINK, puff = 1.0),
        Style("line", Kind.CYCLE, GOLD, ORANGE, PINK, speed = 0.8, shine = 0.5),
        Style("prefix", Kind.CYCLE, GOLD, ORANGE, PINK, speed = 0.8, shine = 0.6, puff = 0.6),
        Style("address", Kind.CYCLE, GOLD, ORANGE, PINK, speed = 0.7, shine = 0.6, puff = 0.5),
        Style("label", Kind.SHINE, 0xffa31a, speed = 0.6, shine = 0.45, puff = 0.3),
        Style("season", Kind.SHINE, 0xf4f0ff, speed = 0.5, shine = 0.8, puff = 0.4),
        Style("event", Kind.RAINBOW, 0xff5adf, puff = 1.0),
    ).onEachIndexed { i, st ->
        // nudge green by 0..6 and blue by 1..4, staying inside 0..255
        fun nudge(v: Int, d: Int) = if (v + d <= 255) v + d else v - d
        val r = (st.c1 shr 16) and 255; val g = (st.c1 shr 8) and 255; val b = st.c1 and 255
        st.marker = (r shl 16) or (nudge(g, i / 4) shl 8) or nudge(b, i % 4 + 1)
    }

    private val byKey = STYLES.associateBy { it.key }

    init {
        val all = STYLES.map { it.marker }
        require(all.toSet().size == all.size) { "TextFx markers must be unique" }
        require(STYLES.none { it.marker == it.c1 }) { "a marker must differ from its colour" }
    }

    /** The marker colour for [key] as a MiniMessage colour ("#b05afe"). */
    fun color(key: String): String = "#%06x".format((byKey[key] ?: error("no text style $key")).marker)

    /** [text] wrapped in the animated style [key]. */
    fun wrap(key: String, text: String): String { val c = color(key); return "<$c>$text</$c>" }

    // ---------------------------------------------------------------- shader
    private fun vec3(c: Int) = "vec3(%.4f, %.4f, %.4f)".format(((c shr 16) and 255) / 255.0, ((c shr 8) and 255) / 255.0, (c and 255) / 255.0)
    private fun ivec3(c: Int, div: Int = 1) = "ivec3(${((c shr 16) and 255) / div}, ${((c shr 8) and 255) / div}, ${(c and 255) / div})"

    /** The replacement rendertype_text.vsh (vanilla 1.21.11's, plus the marker table). */
    fun vertexShader(): String {
        val table = StringBuilder()
        STYLES.forEachIndexed { i, s ->
            val call = "fx(${s.kind.id}, ${vec3(s.c1)}, ${vec3(s.c2)}, ${vec3(s.c3)}, ${"%.3f".format(s.speed)}, ${"%.3f".format(s.shine)}, x, t, top)"
            val kw = if (i == 0) "if" else "else if"
            val pf = "%.2f".format(s.puff)
            table.append("    $kw (c == ${ivec3(s.marker)}) { col.rgb = $call; puff = $pf; }\n")
            table.append("    else if (c == ${ivec3(s.marker, 4)}) { col.rgb = $call * 0.25; puff = $pf; }\n")
        }
        return """#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

// TapX: animated text colours (see TextFx.kt). Everything else is vanilla.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

vec3 tapxHue(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

// a -> b -> c -> a, p in [0, 1)
vec3 tapxLoop(vec3 a, vec3 b, vec3 c, float p) {
    float q = fract(p) * 3.0;
    if (q < 1.0) return mix(a, b, smoothstep(0.0, 1.0, q));
    if (q < 2.0) return mix(b, c, smoothstep(0.0, 1.0, q - 1.0));
    return mix(c, a, smoothstep(0.0, 1.0, q - 2.0));
}

// more vibrant: push the colour away from its own grey
vec3 tapxVivid(vec3 c, float amount) {
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    return clamp(mix(vec3(l), c, amount), 0.0, 1.0);
}

vec3 fx(int kind, vec3 c1, vec3 c2, vec3 c3, float speed, float shine, float x, float t, bool top) {
    vec3 base;
    if (kind == 2) {
        base = tapxHue(t * 0.12 * speed);                           // rainbow, whole word at once
    } else if (kind == 1) {
        base = tapxLoop(c1, c2, c3, t * 0.12 * speed);              // fades through its colours
    } else {
        base = c1;
    }
    base = tapxVivid(base, 1.3);
    base *= 0.94 + 0.06 * sin(t * 2.2 * speed);                    // gentle breathing glow
    // candy gloss: each letter lighter at the top, deeper at the bottom
    base = top ? mix(base, vec3(1.0), 0.38) : base * 0.78;
    // soft shine sweeping left to right every few seconds
    float s = fract(x / 260.0 - t * 0.28 * speed);
    float band = smoothstep(0.0, 0.06, s) * (1.0 - smoothstep(0.06, 0.16, s));
    return mix(base, vec3(1.0), band * 0.55 * shine);
}

// how much a letter at x is swollen right now (0..1): a soft bump rippling along the text
float tapxPuff(float x, float t, float speed) {
    float p = fract(t * 0.45 * speed - x / 160.0);
    return exp(-pow((p - 0.5) * 7.0, 2.0));
}

void main() {
    vec4 pos = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec4 col = Color;
    ivec3 c = ivec3(round(Color.rgb * 255.0));
    float t = GameTime * 1200.0;
    float gs = max(1.0, floor(min(ScreenSize.x / 320.0, ScreenSize.y / 240.0)));
    float x = (pos.x / pos.w * 0.5 + 0.5) * ScreenSize.x / gs;
    // glyph quads are drawn top-left, bottom-left, bottom-right, top-right
    int corner = gl_VertexID % 4;
    bool top = corner == 0 || corner == 3;
    float puff = 0.0;
$table
    if (puff > 0.0) {
        // grow the letter around its centre: each corner moves outwards (up to ~0.8 GUI pixels)
        float d = puff * 0.8 * gs * tapxPuff(x, t, 1.0);
        float dx = (corner == 0 || corner == 1) ? -d : d;
        float dy = top ? d : -d;
        pos.x += dx * 2.0 / ScreenSize.x * pos.w;
        pos.y += dy * 2.0 / ScreenSize.y * pos.w;
    }
    gl_Position = pos;

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = col * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
"""
    }
}
