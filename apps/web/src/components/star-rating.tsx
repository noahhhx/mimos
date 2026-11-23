import { MAX_STARS, ratedLabel, rateLabel } from "@/lib/rating";

const STARS = Array.from({ length: MAX_STARS }, (_, index) => index + 1);

/**
 * A recipe's rating out of five stars. With `onChange` it is a row of
 * buttons, one per star; without, it is a read-only picture of the rating.
 */
export function StarRating({
  rating,
  onChange,
  disabled,
}: {
  rating: number | undefined;
  onChange?: (star: number) => void;
  disabled?: boolean;
}) {
  if (!onChange) {
    return rating === undefined ? null : (
      <span className="stars" role="img" aria-label={ratedLabel(rating)}>
        {STARS.map((star) => (
          <span key={star} className={star <= rating ? "star on" : "star"} aria-hidden="true">
            ★
          </span>
        ))}
      </span>
    );
  }
  return (
    // biome-ignore lint/a11y/useSemanticElements: a <fieldset> has the same group role but brings its own border, padding, and form semantics.
    <span className="stars" role="group" aria-label="Rating">
      {STARS.map((star) => (
        <button
          key={star}
          type="button"
          className={star <= (rating ?? 0) ? "star on" : "star"}
          aria-label={rateLabel(star)}
          aria-pressed={star === rating}
          disabled={disabled}
          onClick={() => onChange(star)}
        >
          ★
        </button>
      ))}
    </span>
  );
}
