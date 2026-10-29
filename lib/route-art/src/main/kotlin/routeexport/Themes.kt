package routeexport

/**
 * Export themes. Placeholder palette until docs/05-design-system tokens land — keep names stable,
 * swap hex values from tokens.json later.
 */
data class Theme(
    val name: String,
    val dark: Boolean,
    val background: String,
    /** Centre of the background vignette. */
    val surface: String,
    val foreground: String,
    val muted: String,
    val route: String,
    val ramp: Ramp,
)

object Themes {
    val MIDNIGHT = Theme("midnight", true, "#0B0F14", "#18212C", "#F3F5F7", "#8B95A5", "#7DD3FC", Ramps.AURORA)
    val DUSK = Theme("dusk", true, "#140F24", "#271D45", "#FBF7FF", "#A79CC4", "#F0ABFC", Ramps.SUNSET)
    val FOREST = Theme("forest", true, "#0D1712", "#18291F", "#ECF5EE", "#8FA897", "#86EFAC", Ramps.MEADOW)
    val PAPER = Theme("paper", false, "#F1ECE2", "#FBF8F2", "#16181D", "#6E6A62", "#1F2937", Ramps.GLACIER)

    val ALL = listOf(MIDNIGHT, DUSK, FOREST, PAPER)
}
