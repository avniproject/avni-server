update user_subject_assignment
set last_modified_date_time = current_timestamp(3) + id * interval '1 millisecond';

update group_subject
set last_modified_date_time = current_timestamp(3) + id * interval '1 millisecond';