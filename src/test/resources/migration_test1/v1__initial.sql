create table simple_types(
    id bigserial primary key,
    string_value varchar unique,
    boolean_value bool,
    int_value int not null,
    long_value bigint,
    created_at timestamp not null default now()
);

create unique index ix_simple_1 on simple_types(string_value,int_value);

