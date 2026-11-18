export const MAX_STARS = 5;

/** The rating a click on star `clicked` leads to: the current star clears it. */
export function nextRating(current: number | undefined, clicked: number): number | null {
  return current === clicked ? null : clicked;
}

/** The accessible name of the button for one star. */
export function rateLabel(star: number): string {
  return `Rate ${star} of ${MAX_STARS} stars`;
}

/** What a read-only rating says, for screen readers. */
export function ratedLabel(rating: number): string {
  return `Rated ${rating} of ${MAX_STARS} stars`;
}
