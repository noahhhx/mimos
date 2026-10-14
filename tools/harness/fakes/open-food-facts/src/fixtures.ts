/**
 * What the fake serves until a scenario changes it: three products as the
 * live Open Food Facts API described them in October 2026 (ADR-0025),
 * trimmed to the fields Mimos asks for plus a few it ignores. Any other
 * barcode is not found.
 */

export type OffProduct = Record<string, unknown>;

/** A solid: nutrition per 100 g, no serving size. */
export const NUTELLA = "3017620422003";
/** A drink: Open Food Facts says `nutrition_data_per: 100g`, but its serving is in ml. */
export const COCA_COLA = "5449000000996";
/** A product with no fat value at all. */
export const MARMITE = "50184453";

export function fixtures(): Map<string, OffProduct> {
  return new Map<string, OffProduct>([
    [
      NUTELLA,
      {
        code: NUTELLA,
        product_name: "Nutella",
        generic_name: "Pâte à tartiner aux noisettes et au cacao",
        brands: "Nutella, Ferrero",
        nutrition_data_per: "100g",
        product_quantity: 400,
        product_quantity_unit: "g",
        serving_quantity_unit: "g",
        nutriments: {
          "energy-kcal_100g": 539,
          "energy-kj_100g": 2252,
          energy_100g: 2252,
          proteins_100g: 6.3,
          carbohydrates_100g: 57.5,
          sugars_100g: 56.3,
          fat_100g: 30.9,
          "saturated-fat_100g": 10.6,
          salt_100g: 0.107,
        },
        nutriscore_grade: "e",
      },
    ],
    [
      COCA_COLA,
      {
        code: COCA_COLA,
        product_name: "Coca-Cola Original",
        generic_name: "Bebida refrescante aromatizada",
        brands: "COCA-COLA SERVICES SA/NV",
        nutrition_data_per: "100g",
        product_quantity: 330,
        product_quantity_unit: "ml",
        serving_quantity: 330,
        serving_quantity_unit: "ml",
        serving_size: "1 portion (330 ml)",
        nutriments: {
          "energy-kcal_100g": 42.4,
          "energy-kj_100g": 180,
          energy_100g: 180,
          proteins_100g: 0,
          carbohydrates_100g: 10.6,
          sugars_100g: 10.6,
          fat_100g: 0,
          "energy-kcal_serving": 140,
        },
        nutriscore_grade: "e",
      },
    ],
    [
      MARMITE,
      {
        code: MARMITE,
        product_name: "Yeast Extract",
        generic_name: "Yeast Extract Spread",
        brands: "Marmite",
        nutrition_data_per: "100g",
        serving_quantity: 8,
        serving_quantity_unit: "g",
        nutriments: {
          "energy-kcal_100g": 260,
          energy_100g: 1087,
          proteins_100g: 34,
          carbohydrates_100g: 30,
          sugars_100g: 1.2,
          salt_100g: 10.8,
        },
      },
    ],
  ]);
}
