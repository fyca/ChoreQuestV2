package com.chorequest.presentation.games

/**
 * Comprehensive question pool for Chore Quiz game
 * Contains hundreds of questions covering all aspects of chores and life skills
 */

// Easy Questions - Basic chores and habits (50+ questions)
val easyQuestions = listOf(
    // App-related questions
    QuizQuestion(
        question = "What should you do after finishing your chores?",
        answers = listOf("Tell your parent", "Hide it", "Do nothing", "Wait for someone else"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Always tell your parent when you finish a chore so they can verify it and you can earn your points!"
    ),
    QuizQuestion(
        question = "How many points do you usually get for completing a chore?",
        answers = listOf("5-20 points", "100 points", "0 points", "1000 points"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Most chores give between 5-20 points depending on how difficult they are. Bigger chores might give more points!"
    ),
    QuizQuestion(
        question = "What should you do with your toys after playing?",
        answers = listOf("Put them away", "Leave them everywhere", "Break them", "Hide them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Putting toys away keeps your room clean and organized. It's an important habit to learn!"
    ),
    
    // Cleaning and tidying
    QuizQuestion(
        question = "What should you do first when cleaning your room?",
        answers = listOf("Pick up toys and clothes", "Start vacuuming", "Wash the windows", "Rearrange furniture"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Always start by picking up items from the floor! This makes it easier to clean the rest of the room."
    ),
    QuizQuestion(
        question = "How often should you brush your teeth?",
        answers = listOf("At least twice a day", "Once a week", "Only when they hurt", "Never"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "You should brush your teeth at least twice a day - once in the morning and once before bed!"
    ),
    QuizQuestion(
        question = "What should you do with dirty clothes?",
        answers = listOf("Put them in the laundry basket", "Leave them on the floor", "Hide them under the bed", "Throw them away"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Dirty clothes should go in the laundry basket so they can be washed. This keeps your room clean!"
    ),
    QuizQuestion(
        question = "When making your bed, what should you do first?",
        answers = listOf("Straighten the bottom sheet", "Fluff the pillows", "Add a blanket", "Jump on it"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Start with the bottom sheet, then add blankets and pillows. A made bed makes your room look neat!"
    ),
    QuizQuestion(
        question = "What should you use to clean a table?",
        answers = listOf("A damp cloth or sponge", "Your hands", "A dry paper towel", "Nothing"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Use a damp cloth or sponge with a little soap to clean tables. Dry it with a clean cloth afterward!"
    ),
    QuizQuestion(
        question = "How should you organize your books?",
        answers = listOf("Put them on a shelf neatly", "Stack them on the floor", "Hide them", "Throw them away"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Books should be organized on a shelf or in a bookcase. This keeps them safe and easy to find!"
    ),
    QuizQuestion(
        question = "What should you do with trash?",
        answers = listOf("Put it in a trash can", "Leave it on the floor", "Hide it", "Eat it"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Always put trash in a trash can! This keeps your home clean and prevents bugs and bad smells."
    ),
    QuizQuestion(
        question = "How long should you wash your hands?",
        answers = listOf("At least 20 seconds", "2 seconds", "1 minute", "Until they're dry"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Wash your hands for at least 20 seconds with soap and water. Sing 'Happy Birthday' twice to time it!"
    ),
    QuizQuestion(
        question = "What should you do with dishes after eating?",
        answers = listOf("Take them to the kitchen", "Leave them on the table", "Hide them", "Break them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Always take your dishes to the kitchen after eating. They can be washed and put away!"
    ),
    QuizQuestion(
        question = "When should you clean up your play area?",
        answers = listOf("After you're done playing", "Never", "Only on weekends", "When someone tells you"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Clean up right after you finish playing! It's easier to put things away when you know where they go."
    ),
    QuizQuestion(
        question = "What should you do with shoes when you come inside?",
        answers = listOf("Put them in their proper place", "Leave them anywhere", "Throw them", "Wear them to bed"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Put your shoes in a designated spot like a shoe rack or by the door. This keeps the floor clean!"
    ),
    QuizQuestion(
        question = "How should you fold clothes?",
        answers = listOf("Neatly and put them away", "Crumple them up", "Leave them unfolded", "Throw them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Fold clothes neatly and put them in drawers or closets. This keeps them from getting wrinkled!"
    ),
    
    // More easy questions - Personal hygiene
    QuizQuestion(
        question = "How often should you take a bath or shower?",
        answers = listOf("Every day or every other day", "Once a month", "Never", "Only on special days"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "You should bathe or shower every day or every other day to stay clean and healthy!"
    ),
    QuizQuestion(
        question = "What should you do before eating?",
        answers = listOf("Wash your hands", "Just start eating", "Skip meals", "Wash your feet"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Always wash your hands with soap and water before eating to keep germs away!"
    ),
    QuizQuestion(
        question = "How should you brush your teeth?",
        answers = listOf("In small circles for 2 minutes", "Very fast", "Only the front teeth", "Don't brush"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Brush in small circles for 2 minutes, making sure to clean all sides of your teeth!"
    ),
    QuizQuestion(
        question = "What should you do after using the bathroom?",
        answers = listOf("Wash your hands", "Skip washing", "Just wipe", "Run away"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Always wash your hands with soap and water after using the bathroom!"
    ),
    QuizQuestion(
        question = "How should you dry your hands?",
        answers = listOf("With a clean towel", "On your clothes", "Don't dry them", "Shake them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Use a clean towel or paper towel to dry your hands after washing!"
    ),
    
    // More easy questions - Basic organization
    QuizQuestion(
        question = "Where should you put your backpack after school?",
        answers = listOf("In a designated spot", "On the floor", "In the trash", "Outside"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Put your backpack in the same spot every day - like a hook or chair - so you can find it easily!"
    ),
    QuizQuestion(
        question = "What should you do with your school papers?",
        answers = listOf("Put them in folders or binders", "Throw them everywhere", "Hide them", "Eat them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Keep school papers organized in folders or binders so you can find them when needed!"
    ),
    QuizQuestion(
        question = "How should you store your art supplies?",
        answers = listOf("In a box or container", "Scatter them around", "Throw them away", "Hide them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Keep art supplies like crayons, markers, and paper in a special box or container!"
    ),
    QuizQuestion(
        question = "What should you do with broken toys?",
        answers = listOf("Tell a parent", "Hide them", "Keep playing with them", "Throw them at siblings"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "If a toy is broken, tell a parent. They can help fix it or decide if it needs to be replaced!"
    ),
    QuizQuestion(
        question = "How should you organize your socks?",
        answers = listOf("Pair them together", "Throw them in a pile", "Wear mismatched", "Lose them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Pair your socks together when putting them away. This makes it easy to find matching pairs!"
    ),
    
    // More easy questions - Kitchen basics
    QuizQuestion(
        question = "What should you do with empty food containers?",
        answers = listOf("Put them in recycling or trash", "Leave them on the counter", "Hide them", "Eat them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Empty containers should go in the recycling bin or trash, not left on counters!"
    ),
    QuizQuestion(
        question = "How should you set the table?",
        answers = listOf("Put plates, utensils, and cups in the right places", "Just throw everything on", "Skip it", "Use your hands"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Set the table properly with plates in the center, forks on the left, and cups on the right!"
    ),
    QuizQuestion(
        question = "What should you do with spills?",
        answers = listOf("Clean them up right away", "Leave them", "Step in them", "Ignore them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Clean up spills immediately with a cloth or paper towel to prevent accidents and stains!"
    ),
    QuizQuestion(
        question = "How should you put away groceries?",
        answers = listOf("Put items in their proper places", "Leave them in bags", "Hide them", "Eat everything"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Put groceries away in the fridge, pantry, or cabinets where they belong!"
    ),
    QuizQuestion(
        question = "What should you do with the milk after using it?",
        answers = listOf("Put it back in the fridge", "Leave it out", "Drink it all", "Pour it down the drain"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Always put milk and other cold items back in the refrigerator right away!"
    ),
    
    // More easy questions - Outdoor and general
    QuizQuestion(
        question = "What should you do with pet food bowls?",
        answers = listOf("Wash them regularly", "Never clean them", "Leave them dirty", "Break them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Pet food and water bowls should be washed regularly to keep pets healthy!"
    ),
    QuizQuestion(
        question = "How should you water plants?",
        answers = listOf("Give them the right amount of water", "Drown them", "Never water them", "Use soda"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Water plants with the right amount - not too much, not too little. Ask a parent how much each plant needs!"
    ),
    QuizQuestion(
        question = "What should you do with mail?",
        answers = listOf("Give it to a parent", "Throw it away", "Hide it", "Eat it"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Give mail to a parent right away. They need to see important letters and bills!"
    ),
    QuizQuestion(
        question = "How should you treat library books?",
        answers = listOf("Keep them clean and return on time", "Draw in them", "Lose them", "Rip pages"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Take good care of library books - keep them clean, don't damage them, and return them on time!"
    ),
    QuizQuestion(
        question = "What should you do with your coat in winter?",
        answers = listOf("Hang it up when you come inside", "Leave it on the floor", "Wear it to bed", "Throw it outside"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Hang your coat on a hook or in a closet when you come inside. This keeps it clean and easy to find!"
    ),
    
    // More easy questions - Safety and responsibility
    QuizQuestion(
        question = "What should you do if you see something dangerous?",
        answers = listOf("Tell an adult immediately", "Touch it", "Hide it", "Ignore it"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "If you see something dangerous like broken glass or chemicals, tell an adult right away!"
    ),
    QuizQuestion(
        question = "How should you behave when doing chores?",
        answers = listOf("Be careful and follow instructions", "Rush through", "Skip steps", "Be careless"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Always be careful and follow instructions when doing chores. Safety comes first!"
    ),
    QuizQuestion(
        question = "What should you do if you don't know how to do a chore?",
        answers = listOf("Ask a parent to show you", "Guess", "Skip it", "Do it wrong"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "If you're not sure how to do something, ask a parent to show you. It's okay to ask for help!"
    ),
    QuizQuestion(
        question = "How should you treat cleaning supplies?",
        answers = listOf("Use them carefully and put them away", "Play with them", "Drink them", "Leave them out"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Cleaning supplies can be dangerous. Use them carefully with adult supervision and always put them away!"
    ),
    QuizQuestion(
        question = "What should you do if you break something?",
        answers = listOf("Tell a parent right away", "Hide it", "Blame someone else", "Pretend it didn't happen"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "If you accidentally break something, tell a parent immediately. It's important to be honest!"
    ),
    
    // More easy questions - Additional cleaning topics
    QuizQuestion(
        question = "What should you do with your bed in the morning?",
        answers = listOf("Make it", "Leave it messy", "Jump on it", "Hide under it"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Making your bed every morning is a great habit! It makes your room look neat and starts your day right!"
    ),
    QuizQuestion(
        question = "How should you clean your hands?",
        answers = listOf("With soap and water for 20 seconds", "Just water", "Wipe on clothes", "Don't wash"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Wash your hands with soap and water for at least 20 seconds. Scrub between fingers and under nails!"
    ),
    QuizQuestion(
        question = "What should you do with your hairbrush?",
        answers = listOf("Clean it regularly", "Never clean it", "Throw it away", "Share it with everyone"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Clean your hairbrush regularly by removing hair and washing it with soap and water!"
    ),
    QuizQuestion(
        question = "How should you store your pajamas?",
        answers = listOf("Fold them and put them away", "Leave them on the floor", "Wear them all day", "Throw them away"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Fold your pajamas and put them in a drawer or under your pillow. Keep them clean for bedtime!"
    ),
    QuizQuestion(
        question = "What should you do with your backpack after school?",
        answers = listOf("Empty it and put it away", "Leave it full on the floor", "Hide it", "Throw it"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Empty your backpack, take out homework and papers, then put it in its designated spot!"
    ),
    QuizQuestion(
        question = "How should you organize your crayons?",
        answers = listOf("Put them in a box or container", "Scatter them around", "Break them", "Lose them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Keep crayons organized in a box or container so they don't get lost or broken!"
    ),
    QuizQuestion(
        question = "What should you do with your lunchbox after school?",
        answers = listOf("Wash it and put it away", "Leave it dirty", "Hide it", "Throw it away"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Wash your lunchbox after school so it's clean and ready for the next day!"
    ),
    QuizQuestion(
        question = "How should you take care of your shoes?",
        answers = listOf("Clean them and put them away", "Leave them dirty", "Wear them in the rain", "Throw them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Clean your shoes when they get dirty and always put them in their proper place!"
    ),
    QuizQuestion(
        question = "What should you do with your water bottle?",
        answers = listOf("Wash it regularly", "Never wash it", "Share it without cleaning", "Throw it away"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Wash your water bottle regularly with soap and water to keep it clean and healthy!"
    ),
    QuizQuestion(
        question = "How should you organize your stuffed animals?",
        answers = listOf("Put them on a shelf or in a basket", "Throw them everywhere", "Hide them", "Throw them away"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Keep stuffed animals organized on a shelf, in a basket, or on your bed. This keeps your room tidy!"
    ),
    
    // More easy questions - Kitchen and food
    QuizQuestion(
        question = "What should you do before helping in the kitchen?",
        answers = listOf("Wash your hands", "Just start cooking", "Skip preparation", "Eat first"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Always wash your hands before helping in the kitchen to keep food safe!"
    ),
    QuizQuestion(
        question = "How should you put away clean dishes?",
        answers = listOf("In their proper places", "Anywhere", "On the floor", "Outside"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Put clean dishes back in their proper places in cabinets and drawers!"
    ),
    QuizQuestion(
        question = "What should you do with fruit peels?",
        answers = listOf("Put them in the compost or trash", "Leave them on the counter", "Eat them", "Hide them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Put fruit peels in the compost bin or trash can, not on counters or floors!"
    ),
    QuizQuestion(
        question = "How should you store bread?",
        answers = listOf("In a bread box or sealed bag", "Leave it open", "In the fridge always", "On the floor"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Store bread in a bread box or sealed bag to keep it fresh and prevent it from getting stale!"
    ),
    QuizQuestion(
        question = "What should you do with empty juice boxes?",
        answers = listOf("Recycle or throw in trash", "Leave them around", "Hide them", "Eat them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Empty juice boxes should go in the recycling bin or trash, not left around!"
    ),
    
    // More easy questions - Bathroom habits
    QuizQuestion(
        question = "What should you do with your towel after showering?",
        answers = listOf("Hang it up to dry", "Leave it on the floor", "Wear it", "Hide it"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Hang your towel on a hook or towel rack so it can dry. This prevents mildew!"
    ),
    QuizQuestion(
        question = "How should you keep the bathroom clean?",
        answers = listOf("Wipe up water and put things away", "Leave it messy", "Never clean it", "Hide things"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Wipe up water from the sink and floor, and put toiletries away after using them!"
    ),
    QuizQuestion(
        question = "What should you do with the toilet paper roll when it's empty?",
        answers = listOf("Replace it with a new one", "Leave it empty", "Throw the holder away", "Hide it"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Replace empty toilet paper rolls with new ones so the next person has what they need!"
    ),
    QuizQuestion(
        question = "How should you store your toothbrush?",
        answers = listOf("Upright in a holder", "Lying down", "On the floor", "In a drawer"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Store your toothbrush upright in a holder so it can air dry and stay clean!"
    ),
    QuizQuestion(
        question = "What should you do with soap in the shower?",
        answers = listOf("Put it back in the soap dish", "Leave it on the floor", "Eat it", "Throw it"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Put soap back in the soap dish after using it. This keeps it clean and prevents waste!"
    ),
    
    // More easy questions - Room organization
    QuizQuestion(
        question = "How should you organize your desk?",
        answers = listOf("Keep it tidy with supplies organized", "Pile everything on", "Leave it messy", "Don't use a desk"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Keep your desk tidy with supplies organized. A clean desk helps you focus on homework!"
    ),
    QuizQuestion(
        question = "What should you do with your school uniform?",
        answers = listOf("Hang it up or fold it neatly", "Leave it on the floor", "Wear it dirty", "Throw it away"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Hang up or fold your school uniform neatly so it's ready for the next day!"
    ),
    QuizQuestion(
        question = "How should you store your sports equipment?",
        answers = listOf("In a designated bag or area", "Scatter it around", "Hide it", "Lose it"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Keep sports equipment in a designated bag or area so you can find it when you need it!"
    ),
    QuizQuestion(
        question = "What should you do with your homework when done?",
        answers = listOf("Put it in your backpack", "Leave it on the table", "Hide it", "Throw it away"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Put completed homework in your backpack so you don't forget it for school!"
    ),
    QuizQuestion(
        question = "How should you organize your art projects?",
        answers = listOf("In a folder or display area", "Scatter them around", "Hide them", "Throw them away"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Keep special art projects in a folder or display them. This shows you're proud of your work!"
    ),
    
    // More easy questions - General habits
    QuizQuestion(
        question = "What should you do when you wake up?",
        answers = listOf("Make your bed and get ready", "Stay in bed all day", "Skip getting ready", "Go back to sleep"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Start your day by making your bed and getting ready. Good morning habits set you up for success!"
    ),
    QuizQuestion(
        question = "How should you treat your belongings?",
        answers = listOf("Take care of them", "Break them", "Lose them", "Throw them away"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Take good care of your belongings so they last a long time and you can enjoy them!"
    ),
    QuizQuestion(
        question = "What should you do with wrappers?",
        answers = listOf("Put them in the trash", "Leave them around", "Hide them", "Eat them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Always put wrappers in the trash can to keep your home clean!"
    ),
    QuizQuestion(
        question = "How should you organize your socks?",
        answers = listOf("Pair them together", "Throw them in a pile", "Wear mismatched", "Lose them"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Pair your socks together when putting them away. This makes it easy to find matching pairs!"
    ),
    QuizQuestion(
        question = "What should you do with your bike?",
        answers = listOf("Put it in its proper place", "Leave it anywhere", "Hide it", "Throw it"),
        correctAnswerIndex = 0,
        difficulty = "easy",
        explanation = "Always put your bike in its designated spot - like a garage or bike rack - when you're done using it!"
    )
)

// Medium Questions - Intermediate skills (50+ questions)  
val mediumQuestions = listOf(
    // App-related questions
    QuizQuestion(
        question = "What is a recurring chore?",
        answers = listOf("A chore that repeats regularly", "A one-time chore", "A difficult chore", "An optional chore"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Recurring chores repeat on a schedule (daily, weekly, or monthly) so you don't have to create them each time!"
    ),
    QuizQuestion(
        question = "What does 'photo proof' mean for a chore?",
        answers = listOf("Taking a picture to show completion", "A photo of the chore", "A drawing", "Nothing"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Photo proof is a picture you take to show that you completed the chore. It helps parents verify your work!"
    ),
    
    // Cleaning techniques
    QuizQuestion(
        question = "What's the best way to clean a mirror?",
        answers = listOf("Use glass cleaner and a lint-free cloth", "Use soap and water", "Wipe with a dry towel", "Spit on it"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Use glass cleaner and a lint-free cloth or paper towel. Wipe in circular motions for best results!"
    ),
    QuizQuestion(
        question = "When vacuuming, what pattern should you use?",
        answers = listOf("Go in straight lines, overlapping slightly", "Random directions", "Only the center", "Skip areas"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Go in straight lines and overlap slightly to make sure you don't miss any spots!"
    ),
    QuizQuestion(
        question = "How should you clean a toilet?",
        answers = listOf("Use toilet cleaner and a brush, then flush", "Just flush", "Use your hands", "Ignore it"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Apply toilet cleaner, scrub with a toilet brush, let it sit, then flush. Always wash your hands after!"
    ),
    QuizQuestion(
        question = "What's the proper way to make a bed?",
        answers = listOf("Bottom sheet, top sheet, blankets, then pillows", "Just throw everything on", "Only the pillows", "Skip it"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Start with the fitted sheet, add the top sheet, then blankets, and finish with pillows arranged nicely!"
    ),
    QuizQuestion(
        question = "How should you organize a closet?",
        answers = listOf("Group similar items together", "Throw everything in", "Leave it messy", "Hide things"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Organize by grouping similar items - shirts together, pants together, etc. This makes finding things easier!"
    ),
    QuizQuestion(
        question = "What should you do when washing dishes?",
        answers = listOf("Scrape food, wash with soap, rinse, and dry", "Just rinse", "Leave them dirty", "Break them"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Scrape off food first, then wash with soapy water, rinse with clean water, and dry with a clean towel!"
    ),
    QuizQuestion(
        question = "How should you sweep a floor?",
        answers = listOf("Start from the edges and work toward the center", "Random directions", "Only the middle", "Skip corners"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Start from the edges and corners, sweeping toward the center. This collects all the dirt in one spot!"
    ),
    QuizQuestion(
        question = "What's the best way to dust furniture?",
        answers = listOf("Use a microfiber cloth or duster, top to bottom", "Use a wet rag", "Blow on it", "Ignore it"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Use a microfiber cloth or duster and work from top to bottom so dust falls down and you don't re-dust areas!"
    ),
    QuizQuestion(
        question = "How should you fold a t-shirt?",
        answers = listOf("Lay flat, fold sides in, then fold in half", "Crumple it", "Don't fold it", "Tie it in a knot"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Lay the shirt flat, fold the sides toward the center, then fold in half. This prevents wrinkles!"
    ),
    QuizQuestion(
        question = "What should you do when cleaning a sink?",
        answers = listOf("Use bathroom cleaner, scrub, then rinse", "Just rinse", "Use soap only", "Skip it"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Apply bathroom cleaner, scrub with a sponge or brush, let it sit briefly, then rinse with water!"
    ),
    QuizQuestion(
        question = "How should you organize your school supplies?",
        answers = listOf("Use containers or organizers for different items", "Throw everything together", "Hide them", "Lose them"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Use pencil cases, folders, and organizers to keep supplies neat. This makes homework time easier!"
    ),
    QuizQuestion(
        question = "What's the proper way to wash your face?",
        answers = listOf("Use warm water and gentle soap, then pat dry", "Just water", "Scrub hard", "Don't wash"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Use warm water and a gentle face soap. Gently wash in circular motions, then pat dry with a clean towel!"
    ),
    QuizQuestion(
        question = "How should you clean windows?",
        answers = listOf("Use glass cleaner and wipe in a W pattern", "Use soap", "Just water", "Don't clean them"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Spray glass cleaner and wipe in a W or Z pattern with a lint-free cloth to avoid streaks!"
    ),
    QuizQuestion(
        question = "What should you do when organizing toys?",
        answers = listOf("Sort by type and put in labeled containers", "Throw everything in one box", "Hide them", "Break them"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Sort toys by type (cars, dolls, blocks, etc.) and store them in labeled containers or bins. This makes cleanup easier!"
    ),
    
    // More medium questions - Laundry
    QuizQuestion(
        question = "What should you check before washing clothes?",
        answers = listOf("Pockets for items and care labels", "Nothing", "The weather", "Your horoscope"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Always check pockets for items like money or tissues, and check care labels for washing instructions!"
    ),
    QuizQuestion(
        question = "How should you sort laundry?",
        answers = listOf("By color and fabric type", "Mix everything", "Only by size", "Don't sort"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Sort by color (whites, darks, colors) and fabric type to prevent colors from bleeding!"
    ),
    QuizQuestion(
        question = "What temperature should you use for most clothes?",
        answers = listOf("Cold or warm water", "Boiling hot", "Ice cold", "Room temperature"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Most clothes should be washed in cold or warm water. Hot water can shrink or damage fabrics!"
    ),
    QuizQuestion(
        question = "How should you hang clothes to dry?",
        answers = listOf("Smooth them out and hang properly", "Crumple them", "Throw them on the floor", "Fold them wet"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Smooth out wrinkles and hang clothes properly on hangers or a clothesline to prevent wrinkles!"
    ),
    QuizQuestion(
        question = "What should you do with clothes that have stains?",
        answers = listOf("Pre-treat the stain before washing", "Just wash normally", "Throw them away", "Hide them"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Pre-treat stains with stain remover before washing. This helps remove the stain more effectively!"
    ),
    
    // More medium questions - Kitchen skills
    QuizQuestion(
        question = "How should you wash fruits and vegetables?",
        answers = listOf("Rinse under running water", "Just wipe them", "Don't wash them", "Use soap"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Rinse fruits and vegetables under cool running water to remove dirt and bacteria!"
    ),
    QuizQuestion(
        question = "What should you do before cooking?",
        answers = listOf("Wash hands and clean the workspace", "Just start cooking", "Skip preparation", "Eat first"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Always wash your hands, clean the counter, and gather ingredients before you start cooking!"
    ),
    QuizQuestion(
        question = "How should you store leftovers?",
        answers = listOf("In airtight containers in the fridge", "Leave them out", "Eat them all", "Throw them away"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Store leftovers in airtight containers in the refrigerator within 2 hours of cooking!"
    ),
    QuizQuestion(
        question = "What should you do with cutting boards after use?",
        answers = listOf("Wash with hot soapy water", "Just wipe them", "Don't clean them", "Use them dirty"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Wash cutting boards with hot soapy water after each use to prevent cross-contamination!"
    ),
    QuizQuestion(
        question = "How should you clean the stove?",
        answers = listOf("Wait for it to cool, then wipe with cleaner", "Clean while hot", "Don't clean it", "Use water on hot stove"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Wait for the stove to cool completely, then wipe with appropriate cleaner. Never use water on a hot stove!"
    ),
    
    // More medium questions - Organization
    QuizQuestion(
        question = "How should you organize a desk?",
        answers = listOf("Keep supplies organized, clear clutter", "Pile everything on", "Leave it messy", "Don't use a desk"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Keep supplies in organizers, clear away clutter, and have a clean workspace for better focus!"
    ),
    QuizQuestion(
        question = "What's the best way to organize a drawer?",
        answers = listOf("Use dividers and group similar items", "Throw everything in", "Leave it empty", "Hide things"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Use drawer dividers or small containers to separate and organize items by type!"
    ),
    QuizQuestion(
        question = "How should you store seasonal items?",
        answers = listOf("In labeled containers", "Scatter them around", "Throw them away", "Hide them"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Store seasonal items like winter clothes or holiday decorations in labeled containers!"
    ),
    QuizQuestion(
        question = "What should you do with items you don't use?",
        answers = listOf("Donate or ask a parent what to do", "Keep everything", "Throw everything away", "Hide them"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "If you don't use something anymore, ask a parent if you can donate it to someone who needs it!"
    ),
    QuizQuestion(
        question = "How should you organize a bookshelf?",
        answers = listOf("By size, author, or topic", "Randomly", "Don't organize", "Throw books away"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Organize books by size, author, or topic. This makes them easier to find and looks neat!"
    ),
    
    // More medium questions - Personal care
    QuizQuestion(
        question = "How should you care for your hair?",
        answers = listOf("Wash regularly and brush gently", "Never wash it", "Pull it hard", "Cut it yourself"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Wash your hair regularly with shampoo, condition it, and brush gently to keep it healthy!"
    ),
    QuizQuestion(
        question = "What should you do with your nails?",
        answers = listOf("Keep them clean and trimmed", "Bite them", "Never cut them", "Paint them with markers"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Keep your nails clean and trimmed. Dirty or long nails can hold germs!"
    ),
    QuizQuestion(
        question = "How often should you change your clothes?",
        answers = listOf("Every day, or when they get dirty", "Once a month", "Never", "Only on weekends"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Change into clean clothes every day, or sooner if they get dirty or sweaty!"
    ),
    QuizQuestion(
        question = "What should you do with wet towels?",
        answers = listOf("Hang them to dry", "Leave them on the floor", "Wear them", "Hide them"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Hang wet towels on a hook or towel rack to dry. This prevents mildew and keeps them fresh!"
    ),
    QuizQuestion(
        question = "How should you store toothbrushes?",
        answers = listOf("Upright in a holder, not touching others", "Lying down together", "In a drawer", "On the floor"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Store toothbrushes upright in a holder so they can air dry. Don't let them touch other brushes!"
    ),
    
    // More medium questions - General cleaning
    QuizQuestion(
        question = "How should you clean baseboards?",
        answers = listOf("Use a damp cloth with cleaner", "Ignore them", "Paint over them", "Remove them"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Use a damp cloth or microfiber cloth with all-purpose cleaner to wipe baseboards!"
    ),
    QuizQuestion(
        question = "What should you do when cleaning light switches?",
        answers = listOf("Turn off power, then wipe with disinfectant", "Clean while on", "Don't clean them", "Paint them"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Light switches get touched a lot! Wipe them with a disinfectant wipe or damp cloth regularly!"
    ),
    QuizQuestion(
        question = "How should you clean door handles?",
        answers = listOf("Wipe with disinfectant regularly", "Never clean them", "Paint them", "Remove them"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Door handles are touched frequently. Wipe them with disinfectant wipes regularly to keep germs away!"
    ),
    QuizQuestion(
        question = "What should you do with cleaning rags after use?",
        answers = listOf("Wash them or throw away disposable ones", "Reuse them dirty", "Hide them", "Eat them"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Wash reusable cleaning rags in hot water, or throw away disposable wipes. Don't reuse dirty rags!"
    ),
    QuizQuestion(
        question = "How should you clean a trash can?",
        answers = listOf("Wash with soap and water, then dry", "Just empty it", "Don't clean it", "Paint it"),
        correctAnswerIndex = 0,
        difficulty = "medium",
        explanation = "Empty the trash, then wash the can with soap and water. Let it dry before putting in a new bag!"
    )
)

// Hard Questions - Advanced skills (50+ questions)
val hardQuestions = listOf(
    // App-related questions
    QuizQuestion(
        question = "What happens to points when you redeem a reward?",
        answers = listOf("They are deducted from your balance", "You get more points", "Nothing happens", "Points double"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "When you redeem a reward, the point cost is subtracted from your balance. Make sure you have enough points!"
    ),
    QuizQuestion(
        question = "What is the difference between 'completed' and 'verified' status?",
        answers = listOf("Completed means you did it, verified means a parent approved it", "They're the same", "Verified comes first", "There's no difference"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "You mark a chore as 'completed' when you finish it, but you only get points after a parent 'verifies' it's done correctly!"
    ),
    
    // Advanced cleaning and organization
    QuizQuestion(
        question = "What's the proper order for deep cleaning a room?",
        answers = listOf("Declutter, dust, vacuum/sweep, mop", "Just vacuum", "Start anywhere", "Skip steps"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Always declutter first, then dust (top to bottom), then vacuum or sweep, and finally mop if needed. This prevents re-cleaning!"
    ),
    QuizQuestion(
        question = "How should you properly fold fitted sheets?",
        answers = listOf("Tuck corners together, then fold into a rectangle", "Just crumple it", "Don't fold it", "Throw it"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Tuck the elastic corners into each other to form a rectangle, then fold like a regular sheet. It takes practice!"
    ),
    QuizQuestion(
        question = "What's the best way to remove tough stains from clothing?",
        answers = listOf("Pre-treat with stain remover, then wash", "Just wash normally", "Ignore it", "Throw it away"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Apply stain remover to the spot, let it sit, then wash in the hottest water safe for the fabric. Check the care label!"
    ),
    QuizQuestion(
        question = "How should you organize a pantry or food storage?",
        answers = listOf("Group by category and use clear containers", "Throw everything in", "Mix everything", "Don't organize"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Group similar items together (canned goods, snacks, baking supplies) and use clear containers so you can see what's inside!"
    ),
    QuizQuestion(
        question = "What's the proper technique for mopping a floor?",
        answers = listOf("Start from the farthest corner and work backward", "Start in the middle", "Random directions", "Don't mop"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Start from the farthest corner and work your way backward so you don't step on the wet, clean floor!"
    ),
    QuizQuestion(
        question = "How should you clean baseboards?",
        answers = listOf("Use a damp cloth with all-purpose cleaner", "Ignore them", "Paint over them", "Remove them"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Use a damp cloth or microfiber cloth with all-purpose cleaner. Work from one end to the other for consistency!"
    ),
    QuizQuestion(
        question = "What's the best way to organize a bathroom?",
        answers = listOf("Use drawer organizers and keep similar items together", "Throw everything in drawers", "Leave it messy", "Hide things"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Use drawer organizers to separate items. Keep toiletries, medicines, and cleaning supplies in separate areas!"
    ),
    QuizQuestion(
        question = "How should you properly clean a shower?",
        answers = listOf("Spray cleaner, let sit, scrub, then rinse", "Just rinse", "Don't clean it", "Paint it"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Spray bathroom cleaner, let it sit for a few minutes to break down soap scum, scrub with a brush, then rinse thoroughly!"
    ),
    QuizQuestion(
        question = "What's the proper way to sort laundry?",
        answers = listOf("By color (whites, darks, colors) and fabric type", "Mix everything", "Only by color", "Don't sort"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Sort by color (whites, darks, colors) and fabric type (delicates, towels, etc.) to prevent colors from bleeding and damage!"
    ),
    QuizQuestion(
        question = "How should you organize a desk for homework?",
        answers = listOf("Keep supplies organized, clear clutter, good lighting", "Pile everything on", "Leave it messy", "Don't use a desk"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Keep supplies in organizers, clear away clutter, ensure good lighting, and have a comfortable chair. A clean space helps you focus!"
    ),
    QuizQuestion(
        question = "What's the best way to clean a microwave?",
        answers = listOf("Heat a bowl of water with lemon, then wipe", "Just wipe it", "Don't clean it", "Use harsh chemicals"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Heat a bowl of water with lemon juice for 2-3 minutes. The steam loosens food, making it easy to wipe clean!"
    ),
    QuizQuestion(
        question = "How should you properly store seasonal clothing?",
        answers = listOf("Clean first, fold neatly, store in labeled containers", "Just throw it in a box", "Leave it in the closet", "Throw it away"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Always clean clothes before storing. Fold or hang neatly, and use labeled containers or vacuum-sealed bags to save space!"
    ),
    QuizQuestion(
        question = "What's the proper way to clean a refrigerator?",
        answers = listOf("Remove items, clean shelves with soapy water, dry, replace items", "Just wipe the outside", "Don't clean it", "Throw everything away"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Remove all items, take out shelves and drawers, wash with warm soapy water, dry thoroughly, then put everything back organized!"
    ),
    QuizQuestion(
        question = "How should you organize a medicine cabinet?",
        answers = listOf("Group by type, check expiration dates, keep out of reach of children", "Throw everything together", "Leave it messy", "Don't organize"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Group medicines by type (pain relief, first aid, etc.), regularly check expiration dates, and always keep medicines out of children's reach!"
    ),
    
    // More hard questions - Advanced techniques
    QuizQuestion(
        question = "What's the best way to remove pet hair from furniture?",
        answers = listOf("Use a lint roller or vacuum with brush attachment", "Use your hands", "Ignore it", "Shave the pet"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Use a lint roller, vacuum with a brush attachment, or a damp rubber glove to remove pet hair effectively!"
    ),
    QuizQuestion(
        question = "How should you clean a washing machine?",
        answers = listOf("Run an empty cycle with vinegar or cleaner", "Never clean it", "Fill it with soap", "Break it"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Run an empty hot cycle with white vinegar or washing machine cleaner monthly to remove buildup and odors!"
    ),
    QuizQuestion(
        question = "What's the proper way to iron clothes?",
        answers = listOf("Set correct temperature, iron in sections, hang immediately", "Use highest heat always", "Iron while wearing", "Don't iron"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Set the iron to the correct temperature for the fabric, iron in sections, and hang clothes immediately to prevent wrinkles!"
    ),
    QuizQuestion(
        question = "How should you deep clean a carpet?",
        answers = listOf("Vacuum first, treat stains, then shampoo or steam clean", "Just vacuum", "Pour water on it", "Don't clean it"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Vacuum thoroughly first, treat any stains, then use a carpet shampooer or steam cleaner. Let it dry completely!"
    ),
    QuizQuestion(
        question = "What's the best way to organize a garage?",
        answers = listOf("Group by category, use shelves and bins, label everything", "Throw everything in", "Don't organize", "Sell everything"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Group items by category (tools, sports equipment, etc.), use shelves and labeled bins, and keep a clear path!"
    ),
    
    // More hard questions - Kitchen mastery
    QuizQuestion(
        question = "How should you properly clean an oven?",
        answers = listOf("Use oven cleaner, let sit, scrub, then wipe clean", "Just wipe it", "Don't clean it", "Use harsh chemicals"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Apply oven cleaner, let it sit according to instructions, scrub with a brush, then wipe clean. Always ventilate the area!"
    ),
    QuizQuestion(
        question = "What's the proper way to sharpen knives?",
        answers = listOf("Use a sharpening stone or take to a professional", "Use a rock", "Don't sharpen them", "Throw them away"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Use a sharpening stone with proper technique, or take knives to a professional. Dull knives are dangerous!"
    ),
    QuizQuestion(
        question = "How should you organize a spice rack?",
        answers = listOf("Alphabetically or by cuisine type", "Randomly", "Don't organize", "Throw them away"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Organize spices alphabetically or by cuisine type. Check expiration dates and replace old spices!"
    ),
    QuizQuestion(
        question = "What's the best way to clean cast iron cookware?",
        answers = listOf("Scrub with salt and oil, don't use soap", "Use harsh soap", "Put in dishwasher", "Don't clean it"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Scrub with coarse salt and a little oil, rinse with hot water, then dry and oil lightly. Avoid soap to preserve seasoning!"
    ),
    QuizQuestion(
        question = "How should you store fresh herbs?",
        answers = listOf("In water like flowers, or wrap in damp paper towel in fridge", "Leave them out", "Freeze them fresh", "Dry them immediately"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Store fresh herbs in a glass of water like flowers, or wrap in a damp paper towel in the refrigerator!"
    ),
    
    // More hard questions - Laundry expertise
    QuizQuestion(
        question = "What's the proper way to fold a dress shirt?",
        answers = listOf("Button it, lay flat, fold sleeves, then fold in thirds", "Just crumple it", "Don't fold it", "Hang it folded"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Button the shirt, lay it flat, fold sleeves back, then fold in thirds. This prevents wrinkles and saves space!"
    ),
    QuizQuestion(
        question = "How should you remove deodorant stains?",
        answers = listOf("Pre-treat with vinegar or stain remover before washing", "Just wash normally", "Scrub hard", "Throw it away"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Pre-treat deodorant stains with white vinegar or a stain remover, let it sit, then wash as usual!"
    ),
    QuizQuestion(
        question = "What's the best way to dry delicate fabrics?",
        answers = listOf("Lay flat on a towel or hang on a padded hanger", "Put in dryer", "Wring them out", "Hang on wire hanger"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Lay delicate items flat on a clean towel or hang on a padded hanger. Never wring or use high heat!"
    ),
    QuizQuestion(
        question = "How should you store wool sweaters?",
        answers = listOf("Fold and store in a drawer, not hung", "Hang them", "Crumple them", "Wear them always"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Fold wool sweaters and store them in a drawer. Hanging can stretch them out of shape!"
    ),
    QuizQuestion(
        question = "What's the proper way to wash white clothes?",
        answers = listOf("Separate whites, use bleach if safe, wash in hot water", "Mix with colors", "Use cold water", "Don't wash them"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Separate whites, check if items are bleach-safe, then wash in hot water with appropriate detergent!"
    ),
    
    // More hard questions - Advanced organization
    QuizQuestion(
        question = "How should you organize a home office?",
        answers = listOf("Create zones for different tasks, use filing system", "Pile everything on desk", "Don't organize", "Work on floor"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Create zones for different tasks (computer area, filing, supplies), and use a filing system for papers!"
    ),
    QuizQuestion(
        question = "What's the best way to organize digital files?",
        answers = listOf("Use folders by category and date, name files clearly", "Put everything in one folder", "Don't organize", "Delete everything"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Create folders by category and date, use clear file names, and regularly delete files you don't need!"
    ),
    QuizQuestion(
        question = "How should you organize a craft room?",
        answers = listOf("Group supplies by type, use clear containers, label everything", "Throw everything together", "Don't organize", "Hide supplies"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Group supplies by type (paints, fabrics, tools), use clear containers so you can see contents, and label everything!"
    ),
    QuizQuestion(
        question = "What's the proper way to organize important documents?",
        answers = listOf("Use filing system by category, keep in safe place", "Throw them in a drawer", "Lose them", "Hide them"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Use a filing system organized by category (medical, school, financial), and keep important documents in a safe place!"
    ),
    QuizQuestion(
        question = "How should you organize a tool collection?",
        answers = listOf("Group by type, use toolboxes or pegboards, label drawers", "Throw in a pile", "Don't organize", "Lose them"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Group tools by type (screwdrivers, hammers, etc.), use toolboxes or pegboards, and label drawers for easy finding!"
    ),
    
    // More hard questions - Specialized cleaning
    QuizQuestion(
        question = "What's the best way to clean grout?",
        answers = listOf("Use grout cleaner and a stiff brush", "Just mop", "Don't clean it", "Paint over it"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Apply grout cleaner, let it sit, then scrub with a stiff brush. Rinse thoroughly and seal if needed!"
    ),
    QuizQuestion(
        question = "How should you clean window tracks?",
        answers = listOf("Vacuum first, then scrub with brush and cleaner", "Just wipe them", "Don't clean them", "Paint them"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Vacuum out debris first, then scrub with a small brush and cleaner. Dry thoroughly to prevent rust!"
    ),
    QuizQuestion(
        question = "What's the proper way to clean a dishwasher?",
        answers = listOf("Run empty cycle with vinegar, clean filter and seals", "Never clean it", "Fill with soap", "Break it"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Run an empty cycle with white vinegar, clean the filter regularly, and wipe down seals and door!"
    ),
    QuizQuestion(
        question = "How should you clean a garbage disposal?",
        answers = listOf("Run with ice and salt, then lemon rinds", "Never clean it", "Put hands in it", "Break it"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Run ice cubes and salt through it to sharpen blades, then lemon rinds to freshen. Always turn it off first!"
    ),
    QuizQuestion(
        question = "What's the best way to remove hard water stains?",
        answers = listOf("Use white vinegar or lemon juice, let sit, then scrub", "Just wipe", "Don't remove them", "Paint over them"),
        correctAnswerIndex = 0,
        difficulty = "hard",
        explanation = "Apply white vinegar or lemon juice, let it sit for a few minutes, then scrub with a brush or cloth!"
    )
)
