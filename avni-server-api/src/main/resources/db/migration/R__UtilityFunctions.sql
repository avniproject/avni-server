CREATE OR REPLACE FUNCTION get_fiscal_year_range(input_date DATE)
    RETURNS TEXT AS
$$
BEGIN
    RETURN TO_CHAR(
                   MAKE_DATE(
                               EXTRACT(YEAR FROM input_date)::INT -
                               CASE WHEN EXTRACT(MONTH FROM input_date) < 4 THEN 1 ELSE 0 END,
                               4, 1
                       ), 'Mon YYYY'
               )
               || ' - ' ||
           TO_CHAR(
                   MAKE_DATE(
                               EXTRACT(YEAR FROM input_date)::INT +
                               CASE WHEN EXTRACT(MONTH FROM input_date) < 4 THEN 0 ELSE 1 END,
                               3, 1
                       ), 'Mon YYYY'
               );
END;
$$ LANGUAGE plpgsql IMMUTABLE;