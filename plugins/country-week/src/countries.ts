/**
 * The countries this plugin knows, matched against the library catalog it
 * is given: by tag first, then by title keywords. A country only gets
 * suggested when the catalog has at least one matching recipe, so the
 * plugin works on any instance's library (ADR-0006 — plugins see the
 * library only).
 */
export type Country = {
  id: string;
  name: string;
  /** Short glyph for the suggestion card (an ISO country code). */
  icon: string;
  /** Cuisine adjective for the card blurb. */
  cuisine: string;
  tags: string[];
  titleWords: string[];
};

export const COUNTRIES: Country[] = [
  {
    id: "italy",
    name: "Italy",
    icon: "IT",
    cuisine: "Italian",
    tags: ["italian"],
    titleWords: ["spaghetti", "pasta", "minestrone", "risotto", "carbonara"],
  },
  {
    id: "india",
    name: "India",
    icon: "IN",
    cuisine: "Indian",
    tags: ["indian", "curry"],
    titleWords: ["curry", "dahl", "dal", "masala", "biryani"],
  },
  {
    id: "greece",
    name: "Greece",
    icon: "GR",
    cuisine: "Greek",
    tags: ["greek"],
    titleWords: ["greek", "feta", "halloumi", "tzatziki"],
  },
  {
    id: "japan",
    name: "Japan",
    icon: "JP",
    cuisine: "Japanese",
    tags: ["japanese"],
    titleWords: ["miso", "sushi", "ramen", "teriyaki", "donburi"],
  },
  {
    id: "lebanon",
    name: "Lebanon",
    icon: "LB",
    cuisine: "Lebanese",
    tags: ["lebanese", "middle-eastern"],
    titleWords: ["mujadara", "hummus", "falafel", "tabbouleh"],
  },
  {
    id: "mexico",
    name: "Mexico",
    icon: "MX",
    cuisine: "Mexican",
    tags: ["mexican"],
    titleWords: ["chili", "taco", "quesadilla", "salsa", "enchilada"],
  },
];
