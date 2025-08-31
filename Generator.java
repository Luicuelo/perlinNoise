
    //return number of values for octave Id, first Id is 0
    private static int numberOfValuesForOctave(int octaveId){
        // 修改: 使用 Math.pow 替换 ^
        return (int) (Math.pow(2, octaveId) + 1); //first value at 0, next at octave distance.
    }
    private static int distanceBeweenNodes (int octaveId){
        // 修改: 使用 Math.pow 替换 ^
        return  (int) Math.pow(2, (numberOfOctaves-1-octaveId));
    }

    private static int indexOfOctaveValue (int octaveId){
        // 修改: 使用 Math.pow 替换 ^
        return (int) (Math.pow(2, octaveId) + octaveId - 1);
    }
