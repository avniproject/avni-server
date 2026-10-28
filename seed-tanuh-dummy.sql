-- Dummy patient seed for staging tanuh_uat — for testing the Physician
-- Review webapp. Creates 8 subjects in Ankali, each with a completed
-- Oral Screening (reusing existing S3 photo URLs so the images render)
-- and a scheduled Physician Review Form encounter.
--
-- Referral distribution: 3 → Belgaum CHC, 3 → Belgaum PHC, 2 → Belgaum HWC.
-- Verdict mix: each subject's photos are mostly Non-Suspicious with 0-8
-- flagged as Suspicious so the "Any image suspicious?" derive logic on the
-- physician review has variety.
--
-- Every row is tagged with legacy_id `TANUH-DUMMY-N` so we can clean up via:
--   DELETE FROM encounter   WHERE legacy_id LIKE 'TANUH-DUMMY-%';
--   DELETE FROM individual  WHERE legacy_id LIKE 'TANUH-DUMMY-%';

BEGIN;
SET ROLE tanuh_uat;

DO $$
DECLARE
  -- Refs (staging tanuh_uat)
  v_ankali_id        bigint    := 300460;
  v_subject_type_id  int       := 2776;
  v_oral_type_id     int       := 5815;
  v_review_type_id   int       := 5810;
  v_user_id          bigint    := 1586;
  v_org_id           int       := 800;
  v_now              timestamptz := now();

  -- Concepts
  v_place_of_referral text := '4a43f83e-26db-40c8-83d8-4317dcfda913';
  v_suspicious        text := '8522fa86-5358-4a74-a974-62110685b1e5';
  v_non_suspicious    text := '487191cf-cb8c-40d3-810e-bca3de4ecb20';

  v_photo_image text[] := ARRAY[
    '91786336-e37d-4b51-8821-ce876516d569',
    'b303d744-7362-4b35-94e8-336b18aba0f0',
    'd2fde9e9-81de-46cf-9215-93ab4e5e7f76',
    '598f3d7e-a4ac-4c48-ae6e-2a5de50a66c1',
    'a6d70ebe-becd-4a63-95df-6908bd1f392f',
    '0ebd94e0-33e7-414d-a52b-77c5be2988b0',
    '3e1eb8b8-c1f9-4e54-8572-733229f9cfbf',
    '94c110d1-aca5-429c-9670-20a87e191a93'
  ];
  v_photo_hw text[] := ARRAY[
    '0db68b48-76ef-4a62-9b83-5e70f04baddb',
    '3566b0f6-29e7-488d-ba0b-cbb022b79c8a',
    '4a663f81-0ea7-44b1-856b-ea62bb887d50',
    '571d8d5f-25c1-446d-bf40-69e4a5e96335',
    '2df1a200-5c62-4f5d-a950-b12dd59215ec',
    'b9eb0715-65b2-4d35-aee7-f2fd0b37d3d7',
    'cdee6a2e-403c-49a2-9597-ebf9e910c4d8',
    'c2d9e387-6f6e-482e-854e-9be3147cb8a4'
  ];
  v_urls text[] := ARRAY[
    'https://s3.ap-south-1.amazonaws.com/staging-user-media/tanuh_uat/f7eaa0be-08f5-4003-bcfc-a4ee10f6d69c.jpg',
    'https://s3.ap-south-1.amazonaws.com/staging-user-media/tanuh_uat/681c9a02-23eb-4be2-8d80-19cff10dd494.jpg',
    'https://s3.ap-south-1.amazonaws.com/staging-user-media/tanuh_uat/1258f49b-5243-41f7-8ce1-c0d568a83380.jpg',
    'https://s3.ap-south-1.amazonaws.com/staging-user-media/tanuh_uat/9dc31b8f-282c-4619-8419-9be56fb33594.jpg',
    'https://s3.ap-south-1.amazonaws.com/staging-user-media/tanuh_uat/2deeb365-0023-42ec-992d-b0bc78fbf4ed.jpg',
    'https://s3.ap-south-1.amazonaws.com/staging-user-media/tanuh_uat/993088e1-6f75-40e6-9dda-78f158f5b670.jpg',
    'https://s3.ap-south-1.amazonaws.com/staging-user-media/tanuh_uat/9cd09b09-98fd-4b19-8d9d-4f3b94ae4507.jpg',
    'https://s3.ap-south-1.amazonaws.com/staging-user-media/tanuh_uat/34b58455-c8af-4ec6-9db2-1f27cd41d3d6.jpg'
  ];

  -- 8 fictional patients. gender = 2026 (Male) or 2027 (Female).
  -- "sus" = which photo slots (1..8) the Health Worker flagged as Suspicious.
  v_patients jsonb := '[
    {"first":"Anjali","last":"Naik","gender":2027,"dob":"1965-04-12","referral":"fd3e3663-902f-41de-8aff-6976ac7a9ddb","sus":[1,3,5]},
    {"first":"Bharat","last":"Patil","gender":2026,"dob":"1958-09-23","referral":"fd3e3663-902f-41de-8aff-6976ac7a9ddb","sus":[2]},
    {"first":"Chitra","last":"Rao","gender":2027,"dob":"1972-01-08","referral":"732152bd-ac98-4321-8a10-5a16489c72e4","sus":[4,6]},
    {"first":"Dilip","last":"Joshi","gender":2026,"dob":"1955-07-19","referral":"732152bd-ac98-4321-8a10-5a16489c72e4","sus":[1,2,3,4,5,6,7,8]},
    {"first":"Eshwari","last":"Bhat","gender":2027,"dob":"1980-11-30","referral":"4b072adc-745a-487d-b931-4ee0b059be96","sus":[]},
    {"first":"Farhan","last":"Kulkarni","gender":2026,"dob":"1968-03-14","referral":"4b072adc-745a-487d-b931-4ee0b059be96","sus":[7,8]},
    {"first":"Gita","last":"Shenoy","gender":2027,"dob":"1962-08-25","referral":"fd3e3663-902f-41de-8aff-6976ac7a9ddb","sus":[]},
    {"first":"Harish","last":"Gowda","gender":2026,"dob":"1975-05-06","referral":"732152bd-ac98-4321-8a10-5a16489c72e4","sus":[3,5,7]}
  ]'::jsonb;

  p          jsonb;
  i          int := 0;
  j          int;
  obs        jsonb;
  sus_set    int[];
  ind_id     bigint;
  legacy_pfx text;
BEGIN
  FOR p IN SELECT * FROM jsonb_array_elements(v_patients) LOOP
    i := i + 1;
    legacy_pfx := 'TANUH-DUMMY-' || i::text;
    sus_set := ARRAY(SELECT (value::text)::int FROM jsonb_array_elements(p->'sus'));

    -- Subject
    INSERT INTO individual (
      uuid, version, address_id, subject_type_id, gender_id,
      first_name, last_name, date_of_birth, date_of_birth_verified,
      registration_date, organisation_id, legacy_id, is_voided, observations,
      created_by_id, last_modified_by_id, created_date_time, last_modified_date_time
    ) VALUES (
      uuid_generate_v4()::text, 1, v_ankali_id, v_subject_type_id, (p->>'gender')::int,
      p->>'first', p->>'last', (p->>'dob')::date, true,
      current_date, v_org_id, legacy_pfx, false, '{}'::jsonb,
      v_user_id, v_user_id, v_now, v_now
    ) RETURNING id INTO ind_id;

    -- Build Oral Screening observations: 8 photos × (image url + HW verdict) + place of referral
    obs := jsonb_build_object(v_place_of_referral, p->>'referral');
    FOR j IN 1..8 LOOP
      obs := obs
        || jsonb_build_object(v_photo_image[j], v_urls[j])
        || jsonb_build_object(
             v_photo_hw[j],
             CASE WHEN j = ANY(sus_set) THEN v_suspicious ELSE v_non_suspicious END
           );
    END LOOP;

    -- Oral Screening (completed 7 days ago, so HW history is plausible)
    INSERT INTO encounter (
      uuid, version, individual_id, encounter_type_id,
      observations, encounter_date_time, organisation_id, is_voided, legacy_id,
      created_by_id, last_modified_by_id, created_date_time, last_modified_date_time
    ) VALUES (
      uuid_generate_v4()::text, 1, ind_id, v_oral_type_id,
      obs, v_now - interval '7 days', v_org_id, false, legacy_pfx || '-OS',
      v_user_id, v_user_id, v_now, v_now
    );

    -- Physician Review Form (scheduled — earliest visit staggered i days out)
    INSERT INTO encounter (
      uuid, version, individual_id, encounter_type_id,
      observations, earliest_visit_date_time, organisation_id, is_voided, legacy_id,
      created_by_id, last_modified_by_id, created_date_time, last_modified_date_time
    ) VALUES (
      uuid_generate_v4()::text, 1, ind_id, v_review_type_id,
      '{}'::jsonb, v_now + (i * interval '1 day'), v_org_id, false, legacy_pfx || '-PR',
      v_user_id, v_user_id, v_now, v_now
    );
  END LOOP;
END $$;

COMMIT;

-- Sanity
SELECT 'individuals' AS what, COUNT(*) FROM individual WHERE legacy_id LIKE 'TANUH-DUMMY-%'
UNION ALL SELECT 'oral_screenings', COUNT(*) FROM encounter WHERE legacy_id LIKE 'TANUH-DUMMY-%-OS'
UNION ALL SELECT 'physician_reviews_scheduled', COUNT(*) FROM encounter WHERE legacy_id LIKE 'TANUH-DUMMY-%-PR';
