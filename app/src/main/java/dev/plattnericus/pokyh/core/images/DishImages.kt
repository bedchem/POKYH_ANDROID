package dev.plattnericus.pokyh.core.images

/**
 * Dish photos at the size they are drawn, as the website asks for them (pokyh-frontend
 * lib/dish-image.ts). The API serves POKYH's own photos (GET /dishes/:id/image, 1254 px square)
 * also as smaller copies (?w=96, 192, 384, 768); loading the whole picture for every 36dp plate
 * made the Mensa slow to fill. Photos from anywhere else are left as they are.
 *
 * Preloading ([ImagePrefetcher]) must ask for the same address the screen draws, since the image
 * cache keeps a picture by its address: so both go through [url] with these sizes.
 */
object DishImages {
    private val own = Regex("/dishes/[^/?#]+/image$")
    private val widths = listOf(96, 192, 384, 768)

    /** A plate in the plan, a chip, a thumbnail on Home. */
    const val PLATE = 192

    /** The next dish on its plate, the dish page, the large photo on Home. */
    const val TABLE = 768

    /** The photo at least [px] wide, as small as the API has it; the original past 768. */
    fun url(url: String?, px: Int): String? {
        if (url.isNullOrBlank() || !own.containsMatchIn(url)) return url
        val w = widths.firstOrNull { it >= px } ?: return url
        return "$url?w=$w"
    }
}
