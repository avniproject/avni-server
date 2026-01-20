-- Replace display order UNIQUE constraints with constraint triggers that provide better error messages

-- Drop existing UNIQUE constraints
ALTER TABLE form_element DROP CONSTRAINT IF EXISTS fe_feg_id_display_order_org_id_is_voided_key;
ALTER TABLE form_element_group DROP CONSTRAINT IF EXISTS feg_f_id_display_order_org_id_is_voided_key;

-- Drop existing triggers if they exist
DROP TRIGGER IF EXISTS form_element_display_order_trigger ON form_element;
DROP TRIGGER IF EXISTS form_element_group_display_order_trigger ON form_element_group;

-- Create trigger function for form_element display order uniqueness
CREATE OR REPLACE FUNCTION check_form_element_display_order_uniqueness() RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM form_element 
        WHERE form_element_group_id = NEW.form_element_group_id 
          AND display_order = NEW.display_order 
          AND organisation_id = NEW.organisation_id 
          AND is_voided = NEW.is_voided
          AND id != NEW.id
    ) THEN
        RAISE EXCEPTION 'Duplicate display_order % found for form element in form element group % for organisation % with is_voided=%', 
            NEW.display_order, NEW.form_element_group_id, NEW.organisation_id, NEW.is_voided;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger function for form_element_group display order uniqueness
CREATE OR REPLACE FUNCTION check_form_element_group_display_order_uniqueness() RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM form_element_group 
        WHERE form_id = NEW.form_id 
          AND display_order = NEW.display_order 
          AND organisation_id = NEW.organisation_id 
          AND is_voided = NEW.is_voided
          AND id != NEW.id
    ) THEN
        RAISE EXCEPTION 'Duplicate display_order % found for form element group in form % for organisation % with is_voided=%', 
            NEW.display_order, NEW.form_id, NEW.organisation_id, NEW.is_voided;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Add triggers (deferrable to check at end of transaction)
CREATE CONSTRAINT TRIGGER form_element_display_order_trigger
    AFTER INSERT OR UPDATE ON form_element
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_form_element_display_order_uniqueness();

CREATE CONSTRAINT TRIGGER form_element_group_display_order_trigger
    AFTER INSERT OR UPDATE ON form_element_group
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_form_element_group_display_order_uniqueness();
