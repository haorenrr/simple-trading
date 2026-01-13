let a= 1+2
console.log("hello js!")

function testPrm(){
    console.log("1")
    let mp = new Promise((resolve, reject) => {
        resolve(1)
        //reject("hello js")
    })

    console.log("2")
    mp
        .then(r => {
                console.log("3, r=" + r)
                return r + 1
            },
            //err => {console.log("4, err=" + err)}
        )
        .then(r => {
            console.log("4, r=" + r)

        })
        .catch(e => {
            console.log("in catch, error=" + e)
        })
        .finally(() => console.log("in finally done"))

    console.log("=============5 over.==============")
}
//testPrm()

function testPrm2(){
    console.log("1")

    let mp = Promise.resolve(1)
    let mp2 = Promise.resolve(2)
    let mp3 = Promise.resolve(3)
    console.log("2")
    Promise.all([mp, mp2, mp3])
        .then(rs => {
            console.log("rs=" + rs)
        })
        .catch(e => {
            console.log("in catch, error=" + e);
        })
        .finally(() => console.log("in finally done"))
}
//testPrm2()

async function testAync(){
    async function f(){
        console.log("6")
        let r = await new Promise((resolve, reject) => {
            setTimeout(() => {console.log("6.5"); reject(1)}, 1000)})
        console.log("7")
        return r
    }
    console.log("1")
    try {
        let ar = await f()
        console.log("3, ar=" + ar)
    }catch (e) {
        console.log("9, in catch, error=" + e);
    }
    console.log("5")
}
//testAync()
//console.log("8")

async function testAync2(){
    //function f(){return Promise.resolve(1)}
    function f(){return 1}
    function g(){return 2}

    try {
        const [rf, rg] = await Promise.all([f(), g()]);
        console.log("rf=" + rf)
        console.log("rg=" + rg)
    }catch (e) {
        console.log("in catch, error=" + e);
    }
}
// testAync2()
// console.log("1")

async function  testFetch(){
    console.log("2")
    await fetch("https://api.zippopotam.us/us/90210", { // 可选的配置项
            method: "GET",
            headers: {"content-type": "application/json"},
            body: undefined, // for POST
            redirect: "follow",
            referrerPolicy: "no-referrer",
            mode: "cors",
            credentials: "same-origin",
            keepalive: false
        })
    .then(res => {
        //console.log(res.headers)
        console.log("status:statusText=" + `${res.status}:${res.statusText}`)
        if (res.ok) { // status: 200-299
            return res.text() // 第一次返回的是响应对象，你得再次异步调用 .json() 才能拿到内容
        }else{ //只有网络错误，fetch才会抛异常，其余的都需要自己处理，比如4xx、5xx等
            // throw new Error(res.status + ":" + res.statusText)
            throw new Error(`${res.status}:${res.statusText}`)
        }
    })
    .then(data => {console.log(data)})
    .catch(e => {console.log("e= " + e)})

    console.log("3")
}
//testFetch()
//console.log("1")

function testArraySort(){
    a = [11, 15, 17, 13, 18, 12, 15, 18]
    b = [1, 5, 7, 3, 8, 2, 0, 4]
    a.sort((a, b) => b - a)
    b.sort((a, b) => b - a)
    console.log(a)
    console.log(b)

    a1 = a.slice(0, 5)
    b1 = b.slice(0, 5)
    console.log(a1)
    console.log(b1)
    c = a1.concat(b1)
    console.log(c)

    //获取最后一个元素
    //let f = [1,3,4,6,7,5]
    let f = []
    let x = f.slice(-1)
    let x0 = x[0]
    console.log(x0)

    o1 = {}
    o1['a']=1;
    o1['d']=4;
    o1['b']=2;

    console.log(o1)

    let oKeys = Object.keys(o1).sort()
    let oVavlue = oKeys.map(k=>o1[k])
    console.log(oVavlue)
    console.log(oKeys)


}
 //testArraySort();

function testObjectMerge(){
    oA = {
      v1:1,
      v2:2,
      v3:3,
    }
    oB = {
        ...oA,
        v4:4,
        v1:10
    }
    console.log("oA=", oA)
    console.log("oB=", oB)
}
//testObjectMerge()

function testArray(){
    let arr = [1,2,3,4,5,6,7,8]
    let r1 = arr.filter(item=>item%2 === 0)
    console.log(r1)
    let arr2 = [
        {id:1, value:11},
        {id:2, value:12},
        {id:3, value:13},
        {id:4, value:14},
        {id:5, value:15},
    ]
    console.log(
        arr2.filter(item=>item.value%2===0)
    )
    console.log(
        arr2.map(item=>item.value)
    )
    console.log(
        "some(): ", arr2.some(item=>item.value === 14),
        "\npop():", arr2.pop(),
        "\narr2:", arr2,
       // "\nshift():", arr2.shift()
    )
    console.log(arr2.shift())
    console.log(arr2)
    console.log(
        arr2.unshift({id:1, value:21}, {id:2, value:22}),
        arr2
    )
    console.log(
        "includes(): ", arr2.includes({id:3, value:13}, 0), //返回false，因为{id:3, value:13}是新对象，内部使用===对比，是看地址，不是对比数值
        "\nsome(): ", arr2.some(item=>item.value === 13),
        "\nfind():", arr2.find(item=>item.value === 13),
    )
}
// testArray()
function testTime(){
    const time1 = '2026-01-09T08:01:07.616Z'
    const time2 = new Date(time1)
    console.log("time2=", time2.toString()) //Fri Jan 09 2026 16:01:07 GMT+0800

    let time3 = time2.toLocaleTimeString() //16:01:07
    console.log("time2.toLcoalString()=", time3)
    console.log("time2.toString=", time2.toTimeString()) //16:01:07 GMT+0800

    console.log("time2.getTime=", time2.getTime()) //1767945667616
    console.log(Math.floor(time2.getTime()/1000))
    const ct = Math.floor(time2.getTime()/1000) * 1000
    let a = {}
    a[ct] = 5

    console.log(a)

}
//testTime()

function testObject(){
    let person = {
        name:'Tom',
        age:25,
        sex:'male',
    }
    console.log("person=")
    console.log(person)

    delete person["name"];
    console.log(person, "\n========")


}
testObject()