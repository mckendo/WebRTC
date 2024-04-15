import http from "http";
import SocketIO from "socket.io";
import express from "express";

const app = express();

app.set("view engine", "pug");
app.set("views" , __dirname + "/views");
app.use("/public", express.static(__dirname + "/public"));

app.get("/", (req, res) => res.render("home"));
app.get("*", (req, res) => res.redirect("/"));

const handleListen = () => console.log(`Listening on http://localhost:3000`); 
const httpServer = http.createServer(app);
const wsServer = SocketIO(httpServer);

wsServer.on("connection", socket =>  {
    console.log("connected!!", socket.handshake.address);
    socket.on("join_room", (roomName) => {
        console.log("join-->welcome", roomName, socket.handshake.address);
        socket.join(roomName);
        socket.to(roomName).emit("welcome");
    });
    socket.on("offer", (offer, roomName) => {
        console.log("offer", offer);
        socket.to(roomName).emit("offer", offer);
    });
    socket.on("answer", (answer, roomName) => {
        console.log("answer", answer);
        socket.to(roomName).emit("answer", answer, roomName);
    });
    socket.on("ice", (ice, roomName) => {
        console.log("ice", ice);
        socket.to(roomName).emit("ice", ice);
    })
});

httpServer.listen(3000, handleListen); 