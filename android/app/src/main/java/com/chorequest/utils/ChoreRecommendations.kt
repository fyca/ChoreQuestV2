package com.chorequest.utils

import java.util.UUID

/**
 * Age-based chore recommendations
 */
object ChoreRecommendations {
    
    /**
     * Get recommended chores for a specific age group
     */
    fun getRecommendedChores(ageGroup: AgeGroup?): List<ChoreRecommendation> {
        if (ageGroup == null) return emptyList()
        
        return when (ageGroup) {
            AgeGroup.TODDLER -> getToddlerChores()
            AgeGroup.PRESCHOOL -> getPreschoolChores()
            AgeGroup.EARLY_ELEMENTARY -> getEarlyElementaryChores()
            AgeGroup.LATE_ELEMENTARY -> getLateElementaryChores()
            AgeGroup.MIDDLE_SCHOOL -> getMiddleSchoolChores()
            AgeGroup.TEEN -> getTeenChores()
        }
    }
    
    /**
     * Get recommended chores for a specific age
     */
    fun getRecommendedChoresForAge(age: Int?): List<ChoreRecommendation> {
        val ageGroup = AgeUtils.getAgeGroup(age)
        return getRecommendedChores(ageGroup)
    }
    
    private fun getToddlerChores(): List<ChoreRecommendation> {
        return listOf(
            ChoreRecommendation("Put toys away", "Help your child put toys in a toy box", 5),
            ChoreRecommendation("Wipe up spills", "Use a cloth to wipe up small spills", 5),
            ChoreRecommendation("Put clothes in hamper", "Place dirty clothes in the laundry basket", 5),
            ChoreRecommendation("Help set table", "Place napkins or utensils on the table", 5),
            ChoreRecommendation("Put books away", "Return books to the bookshelf", 5),
            ChoreRecommendation("Help feed pet", "Pour pet food into bowl with supervision", 5),
            ChoreRecommendation("Pick up crayons", "Put crayons back in the box", 5),
            ChoreRecommendation("Put shoes away", "Place shoes in the designated spot", 5)
        )
    }
    
    private fun getPreschoolChores(): List<ChoreRecommendation> {
        return listOf(
            ChoreRecommendation("Make bed", "Straighten sheets and blankets", 10,
                listOf("Pull up the covers", "Fluff the pillow", "Smooth out wrinkles")),
            ChoreRecommendation("Set table", "Put plates, utensils, and cups on the table", 10,
                listOf("Place plates at each seat", "Put forks and spoons next to plates", "Set cups for drinks")),
            ChoreRecommendation("Put toys away", "Organize toys in their proper places", 5),
            ChoreRecommendation("Feed pet", "Give food and water to family pets", 10,
                listOf("Fill food bowl", "Fill water bowl", "Put bowls in pet's area")),
            ChoreRecommendation("Water plants", "Water indoor plants with help", 5),
            ChoreRecommendation("Clear place at table", "Take own plate and cup to kitchen", 8),
            ChoreRecommendation("Put away shoes", "Place shoes in closet or by door", 5),
            ChoreRecommendation("Help sort laundry", "Put light and dark clothes in separate piles", 8),
            ChoreRecommendation("Wipe table", "Clean table after meals", 8),
            ChoreRecommendation("Put away books", "Return books to bookshelf", 5),
            ChoreRecommendation("Help with groceries", "Carry light items from car", 8),
            ChoreRecommendation("Brush teeth", "Brush teeth morning and night", 5,
                listOf("Get toothbrush and toothpaste", "Brush for 2 minutes", "Rinse mouth", "Put toothbrush away"))
        )
    }
    
    private fun getEarlyElementaryChores(): List<ChoreRecommendation> {
        return listOf(
            ChoreRecommendation("Make bed", "Make bed neatly each morning", 10,
                listOf("Pull up sheets and blankets", "Fluff and place pillows", "Smooth out wrinkles")),
            ChoreRecommendation("Set table", "Set the table for meals", 10,
                listOf("Place plates at each seat", "Set forks, knives, and spoons", "Put napkins and cups")),
            ChoreRecommendation("Clear table", "Take dishes to the kitchen after meals", 10,
                listOf("Collect all plates", "Gather utensils", "Take cups to sink", "Wipe table")),
            ChoreRecommendation("Put away laundry", "Fold and put away clean clothes", 15,
                listOf("Sort clothes by type", "Fold each item neatly", "Put in correct drawers")),
            ChoreRecommendation("Take out trash", "Empty small trash cans", 10),
            ChoreRecommendation("Feed pet", "Feed and water pets", 10,
                listOf("Measure pet food", "Fill food bowl", "Fill water bowl", "Clean up spills")),
            ChoreRecommendation("Water plants", "Water indoor and outdoor plants", 10,
                listOf("Check which plants need water", "Water until soil is moist", "Put watering can away")),
            ChoreRecommendation("Sweep floor", "Sweep kitchen or entryway", 15,
                listOf("Get broom and dustpan", "Sweep all areas", "Collect dirt in dustpan", "Empty dustpan")),
            ChoreRecommendation("Dust furniture", "Dust surfaces with a cloth", 10,
                listOf("Get dusting cloth", "Dust all surfaces", "Put cloth away")),
            ChoreRecommendation("Brush teeth", "Brush teeth properly morning and night", 5,
                listOf("Get toothbrush and toothpaste", "Brush for 2 minutes", "Floss between teeth", "Rinse mouth", "Put everything away")),
            ChoreRecommendation("Get dressed", "Choose and put on clothes independently", 5),
            ChoreRecommendation("Pack backpack", "Pack school bag with needed items", 8,
                listOf("Check homework folder", "Pack lunch", "Include water bottle", "Zip backpack")),
            ChoreRecommendation("Put away groceries", "Help put groceries in correct places", 10),
            ChoreRecommendation("Wipe bathroom sink", "Clean bathroom sink and counter", 10,
                listOf("Wet cloth with water", "Wipe sink and counter", "Dry with clean cloth")),
            ChoreRecommendation("Organize room", "Put everything in its proper place", 15,
                listOf("Pick up toys", "Put clothes away", "Organize books", "Make bed"))
        )
    }
    
    private fun getLateElementaryChores(): List<ChoreRecommendation> {
        return listOf(
            ChoreRecommendation("Make bed", "Make bed neatly each morning", 10,
                listOf("Pull up sheets and blankets", "Fluff and arrange pillows", "Smooth out wrinkles", "Tuck in edges")),
            ChoreRecommendation("Load dishwasher", "Load dirty dishes into dishwasher", 15,
                listOf("Rinse dishes", "Place plates in bottom rack", "Put cups and bowls in top rack", "Add dishwasher soap", "Start dishwasher")),
            ChoreRecommendation("Unload dishwasher", "Put clean dishes away", 15,
                listOf("Open dishwasher", "Put plates in cabinet", "Put cups in cupboard", "Put utensils in drawer", "Put bowls away")),
            ChoreRecommendation("Take out trash", "Take trash and recycling to bins", 15,
                listOf("Tie trash bag", "Take to outside bin", "Put in new trash bag", "Take recycling to bin")),
            ChoreRecommendation("Vacuum room", "Vacuum bedroom or living room", 20,
                listOf("Get vacuum cleaner", "Vacuum all carpeted areas", "Vacuum under furniture", "Empty vacuum bag or canister", "Put vacuum away")),
            ChoreRecommendation("Fold laundry", "Fold and put away clean clothes", 15,
                listOf("Sort clothes by person", "Fold each item neatly", "Match socks", "Put in correct drawers")),
            ChoreRecommendation("Wash dishes", "Wash dishes by hand", 15,
                listOf("Scrape food off plates", "Wash with soapy water", "Rinse with clean water", "Dry dishes", "Put dishes away")),
            ChoreRecommendation("Clean bathroom", "Clean sink and counter", 20,
                listOf("Spray cleaner on sink", "Wipe sink and counter", "Clean mirror", "Wipe faucet", "Put supplies away")),
            ChoreRecommendation("Mow lawn", "Mow small areas with supervision", 30,
                listOf("Check lawn for obstacles", "Start lawn mower", "Mow in straight lines", "Empty grass bag", "Put mower away")),
            ChoreRecommendation("Walk dog", "Walk the family dog", 20,
                listOf("Get leash and bag", "Put leash on dog", "Walk for 15-20 minutes", "Pick up after dog", "Give dog water")),
            ChoreRecommendation("Clean room", "Deep clean bedroom", 25,
                listOf("Pick up all items", "Put clothes in hamper", "Organize desk", "Dust furniture", "Vacuum floor", "Make bed")),
            ChoreRecommendation("Prepare simple meal", "Make breakfast or lunch", 20,
                listOf("Wash hands", "Get ingredients", "Follow recipe", "Cook food", "Set table", "Clean up")),
            ChoreRecommendation("Wash windows", "Clean inside windows", 15,
                listOf("Get window cleaner", "Spray on window", "Wipe with cloth", "Dry with paper towel")),
            ChoreRecommendation("Rake leaves", "Rake and bag leaves", 20,
                listOf("Get rake and bags", "Rake leaves into piles", "Put leaves in bags", "Tie bags", "Put tools away")),
            ChoreRecommendation("Organize closet", "Sort and organize clothes", 15,
                listOf("Take out all clothes", "Sort by type", "Put away neatly", "Donate items that don't fit"))
        )
    }
    
    private fun getMiddleSchoolChores(): List<ChoreRecommendation> {
        return listOf(
            ChoreRecommendation("Clean bathroom", "Clean toilet, sink, and shower", 25,
                listOf("Spray cleaner on surfaces", "Scrub toilet", "Clean sink and mirror", "Scrub shower/tub", "Wipe floor", "Put supplies away")),
            ChoreRecommendation("Vacuum house", "Vacuum all rooms", 25,
                listOf("Get vacuum cleaner", "Vacuum each room", "Move furniture to vacuum under", "Empty vacuum", "Put vacuum away")),
            ChoreRecommendation("Mow lawn", "Mow the entire lawn", 30,
                listOf("Check for obstacles", "Start lawn mower", "Mow in pattern", "Edge along sidewalks", "Empty grass bag", "Clean mower")),
            ChoreRecommendation("Wash car", "Wash the family car", 30,
                listOf("Rinse car with water", "Soap entire car", "Scrub with sponge", "Rinse off soap", "Dry with towel", "Vacuum interior")),
            ChoreRecommendation("Cook simple meal", "Prepare a simple meal with supervision", 30,
                listOf("Plan meal", "Get ingredients", "Follow recipe", "Cook food", "Set table", "Serve meal", "Clean up kitchen")),
            ChoreRecommendation("Do laundry", "Wash, dry, and fold a load of laundry", 25,
                listOf("Sort clothes by color", "Load washing machine", "Add detergent", "Start washer", "Move to dryer", "Fold and put away")),
            ChoreRecommendation("Clean kitchen", "Deep clean kitchen counters and appliances", 25,
                listOf("Clear counters", "Wipe all surfaces", "Clean stovetop", "Wipe microwave", "Clean sink", "Take out trash")),
            ChoreRecommendation("Rake leaves", "Rake and bag leaves", 25,
                listOf("Get rake and bags", "Rake into piles", "Bag leaves", "Tie bags", "Put at curb", "Put tools away")),
            ChoreRecommendation("Babysit siblings", "Watch younger siblings for short periods", 30,
                listOf("Know emergency contacts", "Plan activities", "Prepare snacks", "Supervise play", "Help with homework")),
            ChoreRecommendation("Clean entire room", "Deep clean bedroom", 30,
                listOf("Remove everything from surfaces", "Dust all furniture", "Vacuum floor", "Organize closet", "Make bed", "Take out trash")),
            ChoreRecommendation("Wash dishes", "Wash all dishes by hand", 20,
                listOf("Scrape food", "Soak if needed", "Wash with soap", "Rinse thoroughly", "Dry and put away")),
            ChoreRecommendation("Take out recycling", "Sort and take recycling to bin", 15,
                listOf("Sort by type", "Rinse containers", "Flatten boxes", "Take to bin")),
            ChoreRecommendation("Weed garden", "Remove weeds from garden", 25,
                listOf("Get gardening tools", "Identify weeds", "Pull weeds", "Dispose of weeds", "Put tools away")),
            ChoreRecommendation("Clean windows", "Clean inside and outside windows", 20,
                listOf("Get window cleaner", "Spray on windows", "Wipe with cloth", "Dry with paper towel", "Clean window sills"))
        )
    }
    
    private fun getTeenChores(): List<ChoreRecommendation> {
        return listOf(
            ChoreRecommendation("Deep clean bathroom", "Thoroughly clean all bathroom surfaces", 30,
                listOf("Remove all items", "Spray cleaner everywhere", "Scrub toilet inside and out", "Clean shower/tub", "Scrub sink", "Clean mirror", "Mop floor", "Put everything back")),
            ChoreRecommendation("Cook dinner", "Plan and prepare a family meal", 40,
                listOf("Plan menu", "Create shopping list", "Buy ingredients", "Prepare ingredients", "Cook meal", "Set table", "Serve food", "Clean up kitchen")),
            ChoreRecommendation("Do laundry", "Handle all laundry independently", 25,
                listOf("Sort by color and fabric", "Load washing machine", "Add detergent", "Start washer", "Move to dryer", "Fold all clothes", "Put away in correct rooms")),
            ChoreRecommendation("Mow lawn", "Mow and maintain lawn", 35,
                listOf("Check for obstacles", "Start lawn mower", "Mow entire lawn", "Edge along sidewalks", "Trim around trees", "Empty grass bag", "Clean and store mower")),
            ChoreRecommendation("Clean entire house", "Deep clean multiple rooms", 50,
                listOf("Dust all rooms", "Vacuum all carpets", "Mop all floors", "Clean all bathrooms", "Clean kitchen", "Take out all trash", "Organize common areas")),
            ChoreRecommendation("Grocery shopping", "Shop for groceries with a list", 30,
                listOf("Review shopping list", "Check pantry", "Go to store", "Find all items", "Check prices", "Pay for groceries", "Put groceries away")),
            ChoreRecommendation("Yard work", "Maintain yard and garden", 30,
                listOf("Mow lawn", "Edge sidewalks", "Weed garden", "Water plants", "Trim bushes", "Rake leaves", "Put tools away")),
            ChoreRecommendation("Car maintenance", "Basic car cleaning and maintenance", 35,
                listOf("Wash exterior", "Vacuum interior", "Clean windows", "Check tire pressure", "Check fluid levels", "Organize trunk")),
            ChoreRecommendation("Babysit", "Watch younger siblings independently", 40,
                listOf("Know emergency contacts", "Plan activities", "Prepare meals", "Help with homework", "Supervise play", "Put kids to bed")),
            ChoreRecommendation("Organize garage", "Clean and organize garage", 40,
                listOf("Sort items", "Put tools away", "Organize storage", "Sweep floor", "Take out trash", "Label boxes")),
            ChoreRecommendation("Paint room", "Paint a room with supervision", 50,
                listOf("Move furniture", "Cover floors", "Tape edges", "Prime walls", "Paint walls", "Paint trim", "Clean brushes", "Move furniture back")),
            ChoreRecommendation("Meal prep", "Prepare meals for the week", 45,
                listOf("Plan meals", "Create shopping list", "Buy ingredients", "Prep vegetables", "Cook proteins", "Portion meals", "Store in containers")),
            ChoreRecommendation("Deep clean kitchen", "Thoroughly clean entire kitchen", 35,
                listOf("Empty cabinets", "Clean inside cabinets", "Clean appliances", "Scrub counters", "Clean sink", "Mop floor", "Organize pantry", "Put everything back"))
        )
    }
}

data class ChoreRecommendation(
    val title: String,
    val description: String,
    val suggestedPoints: Int,
    val subtasks: List<String> = emptyList()
)
