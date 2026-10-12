/**
 * What the fake serves until a scenario changes it: one athlete, `i1`, with
 * a week of training around today. Dates are offsets from the day the fake
 * starts or resets, so a scenario on any day finds a planned ride today.
 * The records carry the fields item 12's client reads plus a few it
 * ignores, as intervals.icu's responses do.
 */

export const ATHLETE_ID = "i1";

/** The one key the fake accepts, as the password of HTTP Basic with the username `API_KEY`. Local only; not a secret. */
export const GOOD_KEY = "fake-intervals-icu-key";

export type IcuRecord = Record<string, unknown>;

export interface Calendar {
  athlete: IcuRecord;
  /** Planned workouts by event id. */
  events: Map<number, IcuRecord>;
  /** Completed activities by activity id. */
  activities: Map<string, IcuRecord>;
  /** Wellness records by local date (their `id`). */
  wellness: Map<string, IcuRecord>;
}

/** `today` (YYYY-MM-DD) moved by `days`. */
export function addDays(today: string, days: number): string {
  const date = new Date(`${today}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export function fixtures(today: string): Calendar {
  const day = (offset: number): string => addDays(today, offset);
  const events: IcuRecord[] = [
    {
      id: 41870231,
      start_date_local: `${day(0)}T00:00:00`,
      category: "WORKOUT",
      type: "Ride",
      name: "Sweet spot 3x15",
      moving_time: 5400,
      joules: 1360800,
      icu_intensity: 84.0,
      icu_training_load: 105,
    },
    {
      id: 41870232,
      start_date_local: `${day(2)}T00:00:00`,
      category: "WORKOUT",
      type: "VirtualRide",
      name: "Endurance by heart rate",
      moving_time: 7200,
      icu_intensity: 68.5,
      icu_training_load: 82,
    },
  ];
  const activities: IcuRecord[] = [
    {
      id: "i98765432",
      start_date_local: `${day(-1)}T08:42:10`,
      type: "GravelRide",
      name: "Gravel loop",
      moving_time: 11130,
      icu_joules: 2203560,
      calories: 2410,
      icu_training_load: 168,
    },
  ];
  const wellness: IcuRecord[] = [
    { id: day(-3), weight: 71.4, bodyFat: 14.1, restingHR: 47 },
    { id: day(-2), weight: 71.0, bodyFat: 13.6 },
    { id: day(-1), weight: 70.8, restingHR: 46 },
  ];
  return {
    athlete: {
      id: ATHLETE_ID,
      name: "Test Rider",
      sex: "M",
      weight: 72.0,
      sportSettings: [
        { id: 1, types: ["Run", "VirtualRun", "TrailRun"], ftp: null, lthr: 172 },
        { id: 2, types: ["Ride", "VirtualRide", "GravelRide"], ftp: 282 },
      ],
    },
    events: new Map(events.map((event) => [event.id as number, event])),
    activities: new Map(activities.map((activity) => [activity.id as string, activity])),
    wellness: new Map(wellness.map((record) => [record.id as string, record])),
  };
}
