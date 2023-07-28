update user_group ug set is_voided = true from groups g
where ug.group_id = g.id and g.is_voided = true and ug.is_voided = false;