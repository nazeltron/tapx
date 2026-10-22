package dev.tapx

/**
 * Animated text for the tab list and chat, done by the TapX UI resource pack: its text shader
 * (rendertype_text.vsh, used for all GUI text) looks for a few reserved "marker" colours and draws
 * them animated instead, in a soft "bubbly" style: every letter gets a candy gloss (lighter at the
 * top, a little deeper at the bottom), a gentle breathing glow and a soft shine that sweeps across;
 * some styles also fade through their colours (or the rainbow) over time. Nothing moves. Each marker is the style's own colour nudged by one step, so players without the
 * pack simply see the normal static colour. The shader is generated from [STYLES], so the markers
 * used in messages and the shader always match. Shadows are matched too (vanilla draws them at
 * exactly a quarter of the colour).
 */
object TextFx {
    /** SHINE: one colour; CYCLE: fades c1 -> c2 -> c3 over time; RAINBOW: fades through every hue. */
    enum class Kind(val id: Int) { SHINE(0), CYCLE(1), RAINBOW(2) }

    class Style(
        val key: String, val marker: Int, val kind: Kind, val c1: Int, val c2: Int = c1, val c3: Int = c2,
        /** Animation speed (1 = default) and how strong the white shine is (0..1). */
        val speed: Double = 1.0, val shine: Double = 1.0,
    )

    private val GOLD = 0xffcf3a; private val ORANGE = 0xff7a3a; private val PINK = 0xff5a8a

    val STYLES: List<Style> = listOf(
        // rank and staff tags: strong shine / flow
        Style("tag:owner", 0xb05afe, Kind.CYCLE, 0xb05aff, 0xff5ad8, 0x7a5aff),
        Style("tag:admin", 0xff3a3b, Kind.SHINE, 0xff3a3a),
        Style("tag:mod", 0x4ab8fe, Kind.SHINE, 0x4ab8ff),
        Style("tag:helper", 0x58d06b, Kind.SHINE, 0x58d06a),
        Style("tag:vip", 0xffaa32, Kind.SHINE, 0xffaa33),
        Style("tag:pro", 0x55ff56, Kind.SHINE, 0x55ff55),
        Style("tag:mvp", 0x55fffe, Kind.SHINE, 0x55ffff),
        Style("tag:elite", 0xff5556, Kind.CYCLE, 0xff5555, 0xff9a3a, 0xff3a6a),
        Style("tag:titan", 0xff55fe, Kind.CYCLE, 0xff55ff, 0xb05aff, 0xff8aff),
        Style("tag:legend", 0xffd23b, Kind.RAINBOW, 0xffd23a),
        // names: same colours, softer and slower
        Style("name:owner", 0xb05bff, Kind.CYCLE, 0xb05aff, 0xff5ad8, 0x7a5aff, speed = 0.6, shine = 0.3),
        Style("name:admin", 0xff3b3a, Kind.SHINE, 0xff3a3a, speed = 0.7, shine = 0.35),
        Style("name:mod", 0x4ab9ff, Kind.SHINE, 0x4ab8ff, speed = 0.7, shine = 0.35),
        Style("name:helper", 0x58d16a, Kind.SHINE, 0x58d06a, speed = 0.7, shine = 0.35),
        Style("name:vip", 0xffab33, Kind.SHINE, 0xffaa33, speed = 0.7, shine = 0.35),
        Style("name:pro", 0x56ff55, Kind.SHINE, 0x55ff55, speed = 0.7, shine = 0.35),
        Style("name:mvp", 0x56ffff, Kind.SHINE, 0x55ffff, speed = 0.7, shine = 0.35),
        Style("name:elite", 0xff5655, Kind.CYCLE, 0xff5555, 0xff9a3a, 0xff3a6a, speed = 0.6, shine = 0.3),
        Style("name:titan", 0xff56ff, Kind.CYCLE, 0xff55ff, 0xb05aff, 0xff8aff, speed = 0.6, shine = 0.3),
        Style("name:legend", 0xffd33a, Kind.RAINBOW, 0xffd23a, speed = 0.6),
        // tab list and chat chrome, in the TapX gold -> orange -> pink theme
        Style("title", 0xffcf3b, Kind.CYCLE, GOLD, ORANGE, PINK),
        Style("line", 0xffcf3c, Kind.CYCLE, GOLD, ORANGE, PINK, speed = 0.8, shine = 0.5),
        Style("prefix", 0xffcf3d, Kind.CYCLE, GOLD, ORANGE, PINK, speed = 0.8, shine = 0.6),
        Style("address", 0xff7a3b, Kind.CYCLE, GOLD, ORANGE, PINK, speed = 0.7, shine = 0.6),
        Style("label", 0xffb03b, Kind.SHINE, 0xffb03a, speed = 0.6, shine = 0.45),
        Style("season", 0xeaeaf5, Kind.SHINE, 0xf2f2ff, speed = 0.5, shine = 0.8),
        Style("event", 0xff8ade, Kind.RAINBOW, 0xff8adf),
    )

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
            table.append("    $kw (c == ${ivec3(s.marker)}) { col.rgb = $call; }\n")
            table.append("    else if (c == ${ivec3(s.marker, 4)}) { col.rgb = $call * 0.25; }\n")
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

vec3 fx(int kind, vec3 c1, vec3 c2, vec3 c3, float speed, float shine, float x, float t, bool top) {
    vec3 base;
    if (kind == 2) {
        base = mix(tapxHue(t * 0.12 * speed), vec3(1.0), 0.18);   // pastel rainbow, whole word at once
    } else if (kind == 1) {
        base = tapxLoop(c1, c2, c3, t * 0.12 * speed);              // fades through its colours
    } else {
        base = c1;
    }
    base *= 0.94 + 0.06 * sin(t * 2.2 * speed);                    // gentle breathing glow
    // candy gloss: each letter lighter at the top, a little deeper at the bottom
    base = top ? mix(base, vec3(1.0), 0.5) : base * 0.8;
    // soft shine sweeping left to right every few seconds
    float s = fract(x / 260.0 - t * 0.28 * speed);
    float band = smoothstep(0.0, 0.06, s) * (1.0 - smoothstep(0.06, 0.16, s));
    return mix(base, vec3(1.0), band * 0.55 * shine);
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
$table
    gl_Position = pos;

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = col * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
"""
    }
}
