/**
 * Every country the wheel can land on: the 193 UN member states plus
 * Vatican City, Palestine, Taiwan, and Kosovo. Continents follow the UN M49
 * regions, with the Caribbean and Central America in North America.
 *
 * Library recipes match a country by tag first, then by title keyword, so
 * the plugin works on any instance's library (ADR-0006).
 */

export const CONTINENTS = ["Africa", "Asia", "Europe", "North America", "South America", "Oceania"] as const;
export type Continent = (typeof CONTINENTS)[number];

export type Country = {
  /** ISO 3166-1 alpha-2, uppercase (Kosovo uses the user-assigned XK). */
  code: string;
  name: string;
  continent: Continent;
  /** Cuisine adjective, as in "Peruvian cooking". */
  cuisine: string;
  tags: readonly string[];
  titleWords: readonly string[];
};

type Row = readonly [code: string, name: string, cuisine: string];

const TABLE: Record<Continent, readonly Row[]> = {
  Africa: [
    ["DZ", "Algeria", "Algerian"],
    ["AO", "Angola", "Angolan"],
    ["BJ", "Benin", "Beninese"],
    ["BW", "Botswana", "Botswanan"],
    ["BF", "Burkina Faso", "Burkinabè"],
    ["BI", "Burundi", "Burundian"],
    ["CV", "Cabo Verde", "Cabo Verdean"],
    ["CM", "Cameroon", "Cameroonian"],
    ["CF", "Central African Republic", "Central African"],
    ["TD", "Chad", "Chadian"],
    ["KM", "Comoros", "Comorian"],
    ["CG", "Republic of the Congo", "Congolese"],
    ["CD", "Democratic Republic of the Congo", "Congolese"],
    ["CI", "Côte d'Ivoire", "Ivorian"],
    ["DJ", "Djibouti", "Djiboutian"],
    ["EG", "Egypt", "Egyptian"],
    ["GQ", "Equatorial Guinea", "Equatoguinean"],
    ["ER", "Eritrea", "Eritrean"],
    ["SZ", "Eswatini", "Swazi"],
    ["ET", "Ethiopia", "Ethiopian"],
    ["GA", "Gabon", "Gabonese"],
    ["GM", "Gambia", "Gambian"],
    ["GH", "Ghana", "Ghanaian"],
    ["GN", "Guinea", "Guinean"],
    ["GW", "Guinea-Bissau", "Bissau-Guinean"],
    ["KE", "Kenya", "Kenyan"],
    ["LS", "Lesotho", "Basotho"],
    ["LR", "Liberia", "Liberian"],
    ["LY", "Libya", "Libyan"],
    ["MG", "Madagascar", "Malagasy"],
    ["MW", "Malawi", "Malawian"],
    ["ML", "Mali", "Malian"],
    ["MR", "Mauritania", "Mauritanian"],
    ["MU", "Mauritius", "Mauritian"],
    ["MA", "Morocco", "Moroccan"],
    ["MZ", "Mozambique", "Mozambican"],
    ["NA", "Namibia", "Namibian"],
    ["NE", "Niger", "Nigerien"],
    ["NG", "Nigeria", "Nigerian"],
    ["RW", "Rwanda", "Rwandan"],
    ["ST", "São Tomé and Príncipe", "Santomean"],
    ["SN", "Senegal", "Senegalese"],
    ["SC", "Seychelles", "Seychellois"],
    ["SL", "Sierra Leone", "Sierra Leonean"],
    ["SO", "Somalia", "Somali"],
    ["ZA", "South Africa", "South African"],
    ["SS", "South Sudan", "South Sudanese"],
    ["SD", "Sudan", "Sudanese"],
    ["TZ", "Tanzania", "Tanzanian"],
    ["TG", "Togo", "Togolese"],
    ["TN", "Tunisia", "Tunisian"],
    ["UG", "Uganda", "Ugandan"],
    ["ZM", "Zambia", "Zambian"],
    ["ZW", "Zimbabwe", "Zimbabwean"],
  ],
  Asia: [
    ["AF", "Afghanistan", "Afghan"],
    ["AM", "Armenia", "Armenian"],
    ["AZ", "Azerbaijan", "Azerbaijani"],
    ["BH", "Bahrain", "Bahraini"],
    ["BD", "Bangladesh", "Bangladeshi"],
    ["BT", "Bhutan", "Bhutanese"],
    ["BN", "Brunei", "Bruneian"],
    ["KH", "Cambodia", "Cambodian"],
    ["CN", "China", "Chinese"],
    ["CY", "Cyprus", "Cypriot"],
    ["GE", "Georgia", "Georgian"],
    ["IN", "India", "Indian"],
    ["ID", "Indonesia", "Indonesian"],
    ["IR", "Iran", "Persian"],
    ["IQ", "Iraq", "Iraqi"],
    ["IL", "Israel", "Israeli"],
    ["JP", "Japan", "Japanese"],
    ["JO", "Jordan", "Jordanian"],
    ["KZ", "Kazakhstan", "Kazakh"],
    ["KW", "Kuwait", "Kuwaiti"],
    ["KG", "Kyrgyzstan", "Kyrgyz"],
    ["LA", "Laos", "Lao"],
    ["LB", "Lebanon", "Lebanese"],
    ["MY", "Malaysia", "Malaysian"],
    ["MV", "Maldives", "Maldivian"],
    ["MN", "Mongolia", "Mongolian"],
    ["MM", "Myanmar", "Burmese"],
    ["NP", "Nepal", "Nepali"],
    ["KP", "North Korea", "North Korean"],
    ["OM", "Oman", "Omani"],
    ["PK", "Pakistan", "Pakistani"],
    ["PS", "Palestine", "Palestinian"],
    ["PH", "Philippines", "Filipino"],
    ["QA", "Qatar", "Qatari"],
    ["SA", "Saudi Arabia", "Saudi"],
    ["SG", "Singapore", "Singaporean"],
    ["KR", "South Korea", "Korean"],
    ["LK", "Sri Lanka", "Sri Lankan"],
    ["SY", "Syria", "Syrian"],
    ["TW", "Taiwan", "Taiwanese"],
    ["TJ", "Tajikistan", "Tajik"],
    ["TH", "Thailand", "Thai"],
    ["TL", "Timor-Leste", "Timorese"],
    ["TR", "Turkey", "Turkish"],
    ["TM", "Turkmenistan", "Turkmen"],
    ["AE", "United Arab Emirates", "Emirati"],
    ["UZ", "Uzbekistan", "Uzbek"],
    ["VN", "Vietnam", "Vietnamese"],
    ["YE", "Yemen", "Yemeni"],
  ],
  Europe: [
    ["AL", "Albania", "Albanian"],
    ["AD", "Andorra", "Andorran"],
    ["AT", "Austria", "Austrian"],
    ["BY", "Belarus", "Belarusian"],
    ["BE", "Belgium", "Belgian"],
    ["BA", "Bosnia and Herzegovina", "Bosnian"],
    ["BG", "Bulgaria", "Bulgarian"],
    ["HR", "Croatia", "Croatian"],
    ["CZ", "Czechia", "Czech"],
    ["DK", "Denmark", "Danish"],
    ["EE", "Estonia", "Estonian"],
    ["FI", "Finland", "Finnish"],
    ["FR", "France", "French"],
    ["DE", "Germany", "German"],
    ["GR", "Greece", "Greek"],
    ["HU", "Hungary", "Hungarian"],
    ["IS", "Iceland", "Icelandic"],
    ["IE", "Ireland", "Irish"],
    ["IT", "Italy", "Italian"],
    ["XK", "Kosovo", "Kosovar"],
    ["LV", "Latvia", "Latvian"],
    ["LI", "Liechtenstein", "Liechtensteiner"],
    ["LT", "Lithuania", "Lithuanian"],
    ["LU", "Luxembourg", "Luxembourgish"],
    ["MT", "Malta", "Maltese"],
    ["MD", "Moldova", "Moldovan"],
    ["MC", "Monaco", "Monegasque"],
    ["ME", "Montenegro", "Montenegrin"],
    ["NL", "Netherlands", "Dutch"],
    ["MK", "North Macedonia", "Macedonian"],
    ["NO", "Norway", "Norwegian"],
    ["PL", "Poland", "Polish"],
    ["PT", "Portugal", "Portuguese"],
    ["RO", "Romania", "Romanian"],
    ["RU", "Russia", "Russian"],
    ["SM", "San Marino", "Sammarinese"],
    ["RS", "Serbia", "Serbian"],
    ["SK", "Slovakia", "Slovak"],
    ["SI", "Slovenia", "Slovenian"],
    ["ES", "Spain", "Spanish"],
    ["SE", "Sweden", "Swedish"],
    ["CH", "Switzerland", "Swiss"],
    ["UA", "Ukraine", "Ukrainian"],
    ["GB", "United Kingdom", "British"],
    ["VA", "Vatican City", "Vatican"],
  ],
  "North America": [
    ["AG", "Antigua and Barbuda", "Antiguan"],
    ["BS", "Bahamas", "Bahamian"],
    ["BB", "Barbados", "Barbadian"],
    ["BZ", "Belize", "Belizean"],
    ["CA", "Canada", "Canadian"],
    ["CR", "Costa Rica", "Costa Rican"],
    ["CU", "Cuba", "Cuban"],
    ["DM", "Dominica", "Dominican"],
    ["DO", "Dominican Republic", "Dominican"],
    ["SV", "El Salvador", "Salvadoran"],
    ["GD", "Grenada", "Grenadian"],
    ["GT", "Guatemala", "Guatemalan"],
    ["HT", "Haiti", "Haitian"],
    ["HN", "Honduras", "Honduran"],
    ["JM", "Jamaica", "Jamaican"],
    ["MX", "Mexico", "Mexican"],
    ["NI", "Nicaragua", "Nicaraguan"],
    ["PA", "Panama", "Panamanian"],
    ["KN", "Saint Kitts and Nevis", "Kittitian"],
    ["LC", "Saint Lucia", "Saint Lucian"],
    ["VC", "Saint Vincent and the Grenadines", "Vincentian"],
    ["TT", "Trinidad and Tobago", "Trinidadian"],
    ["US", "United States", "American"],
  ],
  "South America": [
    ["AR", "Argentina", "Argentine"],
    ["BO", "Bolivia", "Bolivian"],
    ["BR", "Brazil", "Brazilian"],
    ["CL", "Chile", "Chilean"],
    ["CO", "Colombia", "Colombian"],
    ["EC", "Ecuador", "Ecuadorian"],
    ["GY", "Guyana", "Guyanese"],
    ["PY", "Paraguay", "Paraguayan"],
    ["PE", "Peru", "Peruvian"],
    ["SR", "Suriname", "Surinamese"],
    ["UY", "Uruguay", "Uruguayan"],
    ["VE", "Venezuela", "Venezuelan"],
  ],
  Oceania: [
    ["AU", "Australia", "Australian"],
    ["FJ", "Fiji", "Fijian"],
    ["KI", "Kiribati", "I-Kiribati"],
    ["MH", "Marshall Islands", "Marshallese"],
    ["FM", "Micronesia", "Micronesian"],
    ["NR", "Nauru", "Nauruan"],
    ["NZ", "New Zealand", "New Zealand"],
    ["PW", "Palau", "Palauan"],
    ["PG", "Papua New Guinea", "Papua New Guinean"],
    ["WS", "Samoa", "Samoan"],
    ["SB", "Solomon Islands", "Solomon Islands"],
    ["TO", "Tonga", "Tongan"],
    ["TV", "Tuvalu", "Tuvaluan"],
    ["VU", "Vanuatu", "Ni-Vanuatu"],
  ],
};

/** Hand-tuned matchers for cuisines the seeded library cooks; every other country matches on its cuisine tag. */
const MATCHERS: Readonly<Record<string, Pick<Country, "tags" | "titleWords">>> = {
  IT: { tags: ["italian"], titleWords: ["spaghetti", "pasta", "minestrone", "risotto", "carbonara"] },
  IN: { tags: ["indian", "curry"], titleWords: ["curry", "dahl", "dal", "masala", "biryani"] },
  GR: { tags: ["greek"], titleWords: ["greek", "feta", "halloumi", "tzatziki"] },
  JP: { tags: ["japanese"], titleWords: ["miso", "sushi", "ramen", "teriyaki", "donburi"] },
  LB: { tags: ["lebanese", "middle-eastern"], titleWords: ["mujadara", "hummus", "falafel", "tabbouleh"] },
  MX: { tags: ["mexican"], titleWords: ["chili", "taco", "quesadilla", "salsa", "enchilada"] },
};

export const COUNTRIES: readonly Country[] = CONTINENTS.flatMap((continent) =>
  TABLE[continent].map(([code, name, cuisine]) => ({
    code,
    name,
    continent,
    cuisine,
    ...(MATCHERS[code] ?? { tags: [cuisine.toLowerCase().replaceAll(" ", "-")], titleWords: [] }),
  })),
);

const BY_CODE: ReadonlyMap<string, Country> = new Map(COUNTRIES.map((country) => [country.code, country]));

/** The country with this code, or `undefined` for anything else (codes arrive from browsers and old databases). */
export function countryByCode(code: string): Country | undefined {
  return BY_CODE.get(code);
}

/** The flag emoji: the code's two letters as regional indicator symbols. */
export function flag(country: Country): string {
  return String.fromCodePoint(...[...country.code].map((letter) => 0x1f1e6 + letter.charCodeAt(0) - 65));
}
