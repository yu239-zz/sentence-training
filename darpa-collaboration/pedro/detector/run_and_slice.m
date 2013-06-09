function I2 = run_and_slice(image, out, padding, model, crop)

    I = imread(image);
    C = imread(crop);
    load(model)
    bbox = process(I, model)
    [I2, box] = cropbox(C, bbox(1,:), padding);
    box
    imwrite(I2, out);
