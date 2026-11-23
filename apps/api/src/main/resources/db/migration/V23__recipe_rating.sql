-- A household's star rating of one of its own recipes (ADR-0035). Null means
-- unrated, and library recipes stay null. Rating is not part of a recipe's
-- saved content, so it never touches updated_at.
alter table recipe add column rating smallint check (rating between 1 and 5);
