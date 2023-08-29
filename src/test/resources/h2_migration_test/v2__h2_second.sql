create table params(
    id bigint auto_increment primary key,
    name varchar not null,
    string_value varchar unique,
    binary_value binary varying
);

create unique index ix_params on params(name);

