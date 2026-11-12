package com.personal.passvault.domain

/**
 * ~500 short, easy-to-type words for passphrases (~9 bits each; the generator computes the exact
 * entropy from the real list size). Swap in the full EFF large list (7,776 words, 12.9 bits each) later if wanted.
 */
object WordList {
    val words: List<String> = """
        able acid aged also apex arch area army atom aunt avid away axis baby back bake bald ball
        band bank barn base bath beam bean bear beef bell belt bend best bike bird bite blue boat
        body bold bolt bone book boot boss bowl brag brew brick bride brim brook broom brush bulb bulk
        bunny burst bush cabin cable cactus cage cake calm camel camp candy cane canoe cape card cargo
        carpet carrot cart case cash castle cave cedar chair chalk charm chef cherry chess chief chin chip
        cider cinema circle city civic clam claw clay cliff clock cloud clover coach coast cobra cocoa
        comet coral cord corn couch cove crab crane crate crisp crow crown cube curry cycle daisy dance
        dawn deck deer delta denim desk dial diary dice dime diner dock dome donut door dove draft dragon
        dream dress drift drum duck dune dust eagle earth easel echo edge elbow elder elk ember empty
        engine epic equal error essay ether event exact fable fact fair falcon fancy farm feast fence
        ferry fever fiber field fig film final finch fire fish flag flame flash fleet flock flute foam
        focus fog forest fork fossil fox frame frost fruit fudge gala game garden gate gecko gem giant
        gift ginger glad glass globe glove glow goat gold golf goose gorge grain grape grass gravy green
        grid grill guava guest guide guitar gulf habit hammer harbor harp hatch hawk hazel heart hedge
        helmet herb hero hill hinge hobby honey hook horse hotel hound house humble husky igloo image
        index inkjet input iris iron island ivory jacket jade jaguar jam jazz jelly jewel jockey jolly
        judge juice jungle kayak kettle key kiwi knee knife koala label ladder lake lamp lantern laser
        latch lava lawn leaf lemon lens level lilac lime linen lion lizard llama lobby lodge logic lotus
        lucky lunar lunch magic magnet mango maple marble march market mask meadow medal melon mentor
        mercy metal meteor mint mirror mobile model money moose motor mouse muffin mural music nacho
        napkin native navy nectar needle nest nickel noble noodle north novel nugget nutmeg oasis ocean
        olive omega onion opal open orbit orange orchid otter oven owl oxygen paddle palace panda panel
        paper parade parcel parrot pasta peach peanut pearl pebble pencil pepper piano pickle pilot pine
        pirate pixel pizza planet plaza plum polar pond pony poppy potato prism puffin pulse pump puzzle
        quail quartz queen quest quick quiet quilt rabbit radar radio raft rain ranch raven razor recipe
        reef relic rhino ribbon rider ridge river robin rocket rodeo roof rose royal ruby rustic saddle
        safari salad salmon salt sand satin saturn scale scarf school scout sea seal seed shadow shark
        shell shelf shield silk silver siren skate sketch sky slate sled slope smile snack snail snow
        soap sofa solar sonic spark spice spider spoon spring squid stable stamp star steam stone storm
        straw stream sugar summit sun surf swan sweet swing syrup table tactic talent tango tank target
        taxi teapot temple tennis tent thunder ticket tiger timber toast token tomato topaz torch tower
        track trail train tree tribe trophy tulip tuna tunnel turtle tutor twig umbrella uncle unicorn
        union upper urban valley vapor vase velvet venus verse vessel violet violin visor vivid volcano
        voyage wafer wagon walnut walrus wand water wave wheat whale whistle willow window winter wizard
        wolf wonder yacht yarn yeti yoga yogurt zebra zenith zero zinc zipper zone
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
}
