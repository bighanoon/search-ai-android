package com.sobhan.searchai;

/** Scene sentences for Show card pictures, one per pictured word.
    Bundled-word sentences come from the app word data imageCaption
    (the literal scene each picture was generated from), except
    candid and bolster, whose pictures were tightened to a closer
    scene under the closest-scene rule, so their sentences describe
    the tightened picture. Rebel and outlaw sentences describe
    their generated pictures. Yelling is NOT here: its verified
    scene lives in VerifiedEntries. A word with no entry returns
    null, and the card shows the picture without a scene sentence
    rather than inventing one. Android-free and unit-testable. */
public class SceneTexts {

  /** The scene sentence for a word, or null when none is stored. */
  public static String forWord(String word) {
    if (word == null) return null;
    String w = word.trim().toLowerCase();
    if (w.equals("austere")) return "A stern teacher glares across a bare, silent classroom — nothing soft, nothing extra.";
    if (w.equals("candid")) return "A person tells an uncomfortable truth plainly, face to face, with an open posture and a direct gaze.";
    if (w.equals("frugal")) return "Grandpa Henry stitches his worn shoe instead of buying new ones — careful, not wasteful.";
    if (w.equals("humble")) return "Holding her prize low, Maya bows modestly to thank her teachers first, not boasting.";
    if (w.equals("tenacious")) return "Soaked reporter still refuses to leave after two years, demanding the truth.";
    if (w.equals("wary")) return "A wary investor leans back, eyes narrowed, clutching her wallet as profits keep falling.";
    if (w.equals("bolster")) return "A lawyer presents strong new evidence that supports and strengthens his case.";
    if (w.equals("contempt")) return "She sneers down at the cheating player and turns away, refusing him any respect.";
    if (w.equals("eloquent")) return "A confident speaker moves her audience to tears with clear, flowing words about clean water.";
    if (w.equals("pragmatic")) return "The mayor fixes the worst pothole first, leaving the fancy plaza plans untouched.";
    if (w.equals("rationale")) return "A planner explains why the park path curves around the trees — the reason behind the choice.";
    if (w.equals("scrutinize")) return "An auditor leans inches from a receipt, eyes narrowed, critically checking every detail.";
    if (w.equals("lucid")) return "A calm teacher explains a tricky atom model so simply her student instantly understands.";
    if (w.equals("coherent")) return "Exhausted Mark speaks calmly, step by step, in a coherent order his listener easily follows.";
    if (w.equals("illuminate")) return "One lamp lights a lone actor on a tiny stage, making him clearly visible.";
    if (w.equals("nostalgia")) return "A man clutches his old teddy bear, smiling tearfully as he longs for childhood.";
    if (w.equals("solemn")) return "A grave officer stands bowed and unsmiling in a silent formal hall — serious and sincere.";
    if (w.equals("vivid")) return "Mara's storm description is so clear her listeners flinch at thunder that isn't there.";
    if (w.equals("ambiguous")) return "Dan shrugs between two possible dates, leaving everyone unsure which one he means.";
    if (w.equals("cursory")) return "Maya gives her email one rushed, half-turned glance — hasty, not thorough.";
    if (w.equals("diminish")) return "Maya's head pain shrinks to a tiny dot as she finally falls asleep.";
    if (w.equals("feasible")) return "Volunteers clear a forest trail, most trash already bagged, so finishing today is possible.";
    if (w.equals("meticulous")) return "The meticulous editor caught a single wrong comma in a 300-page manuscript.";
    if (w.equals("resilient")) return "After losing his job, Tom bounces back, smiling confidently as a new nurse.";
    if (w.equals("in no time")) return "Maya cleans a messy living room super fast, half already sparkling clean behind her.";
    if (w.equals("break the ice")) return "Maya's funny story turns stiff strangers into laughing people eager to talk.";
    if (w.equals("a piece of cake")) return "Maya performs her audition with total ease, smiling and completely relaxed.";
    if (w.equals("under the weather")) return "Maya, slightly sick, stays on the couch, skipping the gym to rest.";
    if (w.equals("spill the beans")) return "A little sister gasps, hand over mouth, after accidentally telling her brother about his surprise party.";
    if (w.equals("cut corners")) return "A hurried builder skips the insulation and seals the bare wall, leaving the house freezing.";
    if (w.equals("once in a blue moon")) return "Two brothers hug tearfully at a doorway after years apart, a visit that almost never happens.";
    if (w.equals("call it a day")) return "We've painted two rooms — let's call it a day and finish the third tomorrow.";
    if (w.equals("bite the bullet")) return "Maya forces herself to book the dreaded dentist appointment — scared, but finally doing it.";
    if (w.equals("hit the nail on the head")) return "She points straight at the team's broken trust, and everyone instantly recognizes the truth.";
    if (w.equals("lucky")) return "Mia beams, finding her lost wallet full of money on the bus by pure chance.";
    if (w.equals("rebel")) return "A person stands firm and defiant before an authority figure, refusing to obey.";
    if (w.equals("outlaw")) return "A lone fugitive walks away from a town at dusk, living outside the law.";
    return null;
  }
}
