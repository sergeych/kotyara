create table params(
    name varchar not null,
    string_value varchar unique,
    binary_value bytea
);

create unique index ix_params on params(lower(name));

