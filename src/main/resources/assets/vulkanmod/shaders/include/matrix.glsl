mat2 mat2_rotate_z(float radians) {
    float s = sin(radians);
    float c = cos(radians);

    return mat2(
        c, -s,
        s, c
    );
}
